package pro.fakeemail;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/** Sends requests: URL and headers, per-attempt timeout, retries, error mapping. Shared by derived clients. */
final class HttpCore {
    /** Sleeps between retries and polls; tests replace it to run instantly. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final long MAX_RETRY_AFTER_MS = 60_000;
    private static final long BACKOFF_BASE_MS = 500;
    private static final long BACKOFF_MAX_MS = 8_000;
    private static final long MAX_RETRIED_503_RETRY_AFTER_MS = 5_000;

    final String apiKey;
    final String baseUrl;
    final String userAgent;
    final Duration timeout;
    final int maxRetries;
    final HttpClient http;
    final Sleeper sleeper;
    final LongSupplier clock;
    final DoubleSupplier random;

    HttpCore(
            String apiKey,
            String baseUrl,
            String userAgent,
            Duration timeout,
            int maxRetries,
            HttpClient http,
            Sleeper sleeper,
            LongSupplier clock,
            DoubleSupplier random) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.http = http;
        this.sleeper = sleeper;
        this.clock = clock;
        this.random = random;
    }

    /** One API call. {@code query} values that are {@code null} are left out. */
    static final class Call {
        final String method;
        final String path;
        final Map<String, String> query = new LinkedHashMap<>();
        Object body;
        /** Per-attempt timeout for this call; {@code null} means the client's. */
        Duration timeout;
        /** {@code null} means the client's. */
        Integer maxRetries;
        /**
         * Clock time (ms) the call must end by: the caller has given up then. Attempts are cut to it and no
         * retry is started past it; {@code null} for none.
         */
        Long deadline;

        Call(String method, String path) {
            this.method = method;
            this.path = path;
        }

        Call query(String name, Object value) {
            if (value != null) {
                query.put(name, String.valueOf(value));
            }
            return this;
        }

        Call body(Object body) {
            this.body = body;
            return this;
        }

        Call options(RequestOptions options) {
            if (options.timeout() != null) {
                timeout = options.timeout();
            }
            if (options.maxRetries() != null) {
                maxRetries = options.maxRetries();
            }
            return this;
        }
    }

    long now() {
        return clock.getAsLong();
    }

    /** Parsed JSON of a successful response. */
    Object json(Call call) {
        return parseBody(send(call).body());
    }

    /** For {@code wait}, which answers {@code 204} on timeout: {@code null} then, or for an empty body. */
    Object jsonOrNull(Call call) {
        HttpResponse<byte[]> response = send(call);
        if (response.statusCode() == 204 || response.body() == null || response.body().length == 0) {
            return null;
        }
        return parseBody(response.body());
    }

    /**
     * Sends the call, retrying what is safe to repeat. With a {@link Call#deadline}, every attempt's timeout is
     * cut to the time left and no retry (pause included) is started that would end past it.
     *
     * <p>A {@code DELETE} retried after a lost response (timeout, connection error, 502/504/52x) that gets
     * {@code 404} succeeded: the lost attempt did the deletion. That {@code 404} response is returned.
     */
    HttpResponse<byte[]> send(Call call) {
        int retries = call.maxRetries != null ? call.maxRetries : maxRetries;
        Duration callTimeout = call.timeout != null ? call.timeout : timeout;
        // DELETE is idempotent here too: a repeated delete never has a second effect.
        boolean idempotent = !call.method.equals("POST");
        boolean delete = call.method.equals("DELETE");
        // An earlier attempt may have reached the handler although its response was lost.
        boolean mayHaveApplied = false;

        for (int attempt = 0; ; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw cancelled(new InterruptedException());
            }
            Duration attemptTimeout = callTimeout;
            if (call.deadline != null) {
                long left = call.deadline - now();
                if (left <= 0) {
                    throw new APITimeoutError("the deadline passed before the request was sent", null);
                }
                if (left < attemptTimeout.toMillis()) {
                    attemptTimeout = Duration.ofMillis(left);
                }
            }
            HttpResponse<byte[]> response;
            try {
                response = http.send(request(call, attemptTimeout), HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException e) {
                FakeEmailError error = e instanceof HttpTimeoutException
                        ? new APITimeoutError("request timed out after " + attemptTimeout.toMillis() + " ms", e)
                        : new APIConnectionError("connection error: " + describe(e), e);
                mayHaveApplied = true;
                long delay = backoff(attempt);
                if (idempotent && attempt < retries && fits(delay, call.deadline)) {
                    sleep(delay);
                    continue;
                }
                throw error;
            } catch (InterruptedException e) {
                throw cancelled(e);
            }

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response;
            }
            if (status == 404 && delete && mayHaveApplied) {
                return response;
            }

            APIError error = APIError.fromResponse(status, response.headers(), errorBody(response.body()), now());
            Long retryAfter = RetryAfter.parseMillis(
                    response.headers().firstValue("retry-after").orElse(null), now());
            // 429 and 503 are refused before any effect, so even POST is safe to repeat; 502/504/52x may have
            // reached the handler, so only idempotent requests are repeated. A 503 with a long Retry-After (no
            // domain to issue an address on) will not clear within a retry: it is thrown at once.
            boolean lost = isLostResponse(status);
            mayHaveApplied |= lost;
            boolean retryable = status == 429
                    || (status == 503 && (retryAfter == null || retryAfter <= MAX_RETRIED_503_RETRY_AFTER_MS))
                    || (idempotent && lost);
            if (retryable && attempt < retries) {
                long delay = retryAfter == null ? backoff(attempt) : Math.min(retryAfter, MAX_RETRY_AFTER_MS);
                if (fits(delay, call.deadline)) {
                    sleep(delay);
                    continue;
                }
            }
            throw error;
        }
    }

    /** 502, 504 and Cloudflare's 520..524: the request may have reached the handler, the response is lost. */
    static boolean isLostResponse(int status) {
        return status == 502 || status == 504 || (status >= 520 && status <= 524);
    }

    void sleep(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            throw cancelled(e);
        }
    }

    /** Restores the interrupt flag and turns the interruption into the unchecked cancellation. */
    static CancellationException cancelled(InterruptedException e) {
        Thread.currentThread().interrupt();
        CancellationException cancelled = new CancellationException("interrupted");
        cancelled.initCause(e);
        return cancelled;
    }

    private HttpRequest request(Call call, Duration attemptTimeout) {
        StringBuilder url = new StringBuilder(baseUrl).append(call.path);
        char sep = '?';
        for (Map.Entry<String, String> e : call.query.entrySet()) {
            url.append(sep).append(form(e.getKey())).append('=').append(form(e.getValue()));
            sep = '&';
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(attemptTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent);
        if (call.body != null) {
            builder.header("Content-Type", "application/json")
                    .method(
                            call.method,
                            HttpRequest.BodyPublishers.ofByteArray(
                                    Json.write(call.body).getBytes(StandardCharsets.UTF_8)));
        } else {
            builder.method(call.method, HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    private long backoff(int attempt) {
        long base = attempt >= 5 ? BACKOFF_MAX_MS : Math.min(BACKOFF_BASE_MS << attempt, BACKOFF_MAX_MS);
        // Up to 25% jitter downwards, so parallel test workers do not retry in lockstep.
        return Math.round(base * (1 - 0.25 * random.getAsDouble()));
    }

    private boolean fits(long delay, Long deadline) {
        return deadline == null || now() + delay < deadline;
    }

    private static Object parseBody(byte[] body) {
        String text = new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8);
        try {
            return Json.parse(text);
        } catch (Json.ParseException e) {
            throw new FakeEmailError("the API answered with invalid JSON: " + e.getMessage(), e);
        }
    }

    /** JSON if it parses, else the raw text (a proxy's HTML page), else {@code null} when empty. */
    private static Object errorBody(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        String text = new String(body, StandardCharsets.UTF_8);
        try {
            return Json.parse(text);
        } catch (Json.ParseException e) {
            return text;
        }
    }

    private static String describe(Throwable e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) {
            return message + " (" + cause.getMessage() + ")";
        }
        return message;
    }

    /** Query component, {@code application/x-www-form-urlencoded} like JavaScript's URLSearchParams. */
    private static String form(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Path segment for an address or id, like JavaScript's {@code encodeURIComponent}: {@code @} is encoded too,
     * which the server accepts.
     */
    static String seg(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || "-_.!~*'()".indexOf(c) >= 0) {
                out.append((char) c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)));
                out.append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return out.toString();
    }
}
