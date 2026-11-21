package pro.fakeemail;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import pro.fakeemail.models.DetailedMessage;
import pro.fakeemail.models.SimpleMessage;

/**
 * Client for the fake-email.pro Public API. Thread-safe: create one and share it across tests.
 *
 * <pre>{@code
 * FakeEmail mail = new FakeEmail(); // reads FAKE_EMAIL_API_KEY
 * String address = mail.mailboxes().create().address();
 * // ...sign up with the address in your app...
 * String code = mail.waitForCode(address); // "048213"
 * }</pre>
 *
 * <p>Server-side only: the key must never reach browser code (the API has no CORS).
 *
 * <p>Every method blocks the calling thread. Interrupting it cancels the call with a {@link
 * java.util.concurrent.CancellationException}.
 */
public final class FakeEmail implements AutoCloseable {
    /** Version of this SDK, as sent in the {@code User-Agent} header. */
    public static final String VERSION = Version.read();

    /** {@code https://api.fake-email.pro}. */
    public static final String DEFAULT_BASE_URL = "https://api.fake-email.pro";

    static final String API_KEY_ENV = "FAKE_EMAIL_API_KEY";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final int MAX_SERVER_WAIT_S = 60;
    // A 204 that comes back far earlier than asked (a server restart) is not retried in a tight loop.
    private static final long EARLY_EMPTY_PAUSE_MS = 1_000;
    // Between attempts of the wait loop after a transient failure without Retry-After.
    private static final long TRANSIENT_PAUSE_MS = 1_000;
    // The 404 message of a wait whose `after` names a message that is not in the mailbox.
    private static final String AFTER_NOT_FOUND = "message in after not found";

    private final HttpCore http;
    private final boolean ownsHttpClient;
    private final RequestOptions options;
    private final MailboxesResource mailboxes;
    private final MessagesResource messages;
    private final DomainsResource domains;
    private final LimitsResource limits;

    /** A client with the key from the {@code FAKE_EMAIL_API_KEY} environment variable and default settings. */
    public FakeEmail() {
        this(builder());
    }

    private FakeEmail(Builder b) {
        String apiKey = b.apiKey != null ? b.apiKey : b.env.apply(API_KEY_ENV);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new FakeEmailError("missing API key: call FakeEmail.builder().apiKey(...) or set "
                    + API_KEY_ENV
                    + " (create one at https://fake-email.pro/settings/api-keys)");
        }
        String baseUrl = trimTrailingSlashes(b.baseUrl);
        URI base;
        try {
            base = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid baseUrl: " + b.baseUrl, e);
        }
        if (!"https".equalsIgnoreCase(base.getScheme()) && !"http".equalsIgnoreCase(base.getScheme())) {
            throw new IllegalArgumentException("baseUrl must be an http(s) URL: " + b.baseUrl);
        }
        String sdkToken = "fake-email-pro-java/" + VERSION;
        String userAgent = b.userAgent != null && !b.userAgent.isEmpty() ? sdkToken + " " + b.userAgent : sdkToken;
        this.ownsHttpClient = b.httpClient == null;
        HttpClient client = b.httpClient != null ? b.httpClient : HttpClient.newHttpClient();
        this.http =
                new HttpCore(apiKey, baseUrl, userAgent, b.timeout, b.maxRetries, client, b.sleeper, b.clock, b.random);
        this.options = RequestOptions.none();
        this.mailboxes = new MailboxesResource(http, options);
        this.messages = new MessagesResource(http, options);
        this.domains = new DomainsResource(http, options);
        this.limits = new LimitsResource(http, options);
    }

    private FakeEmail(FakeEmail parent, RequestOptions options) {
        this.http = parent.http;
        this.ownsHttpClient = false;
        this.options = options;
        this.mailboxes = new MailboxesResource(http, options);
        this.messages = new MessagesResource(http, options);
        this.domains = new DomainsResource(http, options);
        this.limits = new LimitsResource(http, options);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Create, list, get and delete mailboxes. */
    public MailboxesResource mailboxes() {
        return mailboxes;
    }

    /** List, wait for, read and delete messages; raw EML and attachments. */
    public MessagesResource messages() {
        return messages;
    }

    /** Domains you can create mailboxes on. */
    public DomainsResource domains() {
        return domains;
    }

    /** Your plan's numbers. */
    public LimitsResource limits() {
        return limits;
    }

    /** The API base URL, without trailing slashes. */
    public String baseUrl() {
        return http.baseUrl;
    }

    /**
     * A view of this client whose calls use {@code options} (timeout per attempt, retries). It shares the
     * connection pool; closing it does nothing.
     */
    public FakeEmail withOptions(RequestOptions options) {
        Objects.requireNonNull(options, "options");
        return new FakeEmail(this, this.options.merge(options));
    }

    /**
     * Waits up to 60 s for the earliest message in the mailbox (even one that arrived before the call).
     *
     * @throws WaitTimeoutError at the deadline
     */
    public SimpleMessage waitForMessage(String address) {
        return waitForMessage(address, WaitOptions.defaults());
    }

    /**
     * Waits until a matching message arrives, repeating server long polls until the options' timeout. Rate
     * limits (429), 502/503/504/52x, connection errors and timeouts are waited out until the deadline.
     *
     * @throws WaitTimeoutError at the deadline (its cause is the last transient failure, if any)
     */
    public SimpleMessage waitForMessage(String address, WaitOptions options) {
        return waitLoop(address, options, (m, deadline) -> m);
    }

    /**
     * Waits up to 60 s for a message with a one-time code and returns the code.
     *
     * @throws WaitTimeoutError at the deadline
     */
    public String waitForCode(String address) {
        return waitForCode(address, WaitOptions.defaults());
    }

    /**
     * Waits for a message with a one-time code and returns the code. Uses the code found on delivery, else the
     * first code of the full message; a message without any code is skipped.
     *
     * @throws WaitTimeoutError at the deadline
     */
    public String waitForCode(String address, WaitOptions options) {
        return waitLoop(address, options, (m, deadline) -> {
            if (m.code() != null && !m.code().isEmpty()) {
                return m.code();
            }
            DetailedMessage full = messages.getWithin(address, m.id(), deadline);
            return full.codes().isEmpty() ? null : full.codes().get(0);
        });
    }

    /**
     * Waits up to 60 s for a message with a confirm / sign-in link and returns its URL.
     *
     * @throws WaitTimeoutError at the deadline
     */
    public String waitForLink(String address) {
        return waitForLink(address, WaitOptions.defaults());
    }

    /**
     * Waits for a message with a confirm / sign-in link and returns its URL. Uses the link found on delivery,
     * else the first link of the full message; a message without links is skipped.
     *
     * @throws WaitTimeoutError at the deadline
     */
    public String waitForLink(String address, WaitOptions options) {
        return waitLoop(address, options, (m, deadline) -> {
            if (m.confirmLink() != null && !m.confirmLink().isEmpty()) {
                return m.confirmLink();
            }
            DetailedMessage full = messages.getWithin(address, m.id(), deadline);
            return full.links().isEmpty() ? null : full.links().get(0).url();
        });
    }

    /**
     * The loop behind the wait helpers. It owns the retries: each {@code wait} request is sent once, with a
     * server timeout recomputed from the time left, and transient failures (429, 502, 503, 504, 520..524,
     * connection errors and timeouts) are waited out until the deadline, which then is a {@link WaitTimeoutError}.
     * {@code extract} gets the deadline for any extra request it makes.
     */
    private <T> T waitLoop(String address, WaitOptions options, Extractor<T> extract) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(options, "options");
        Duration timeout = options.timeout();
        long deadline = http.now() + Math.max(0, timeout.toMillis());
        Predicate<SimpleMessage> match = options.match();
        String after = options.after();
        // The last message the loop moved `after` past, for the fallback when it is deleted meanwhile.
        SimpleMessage skipped = null;
        FakeEmailError lastFailure = null;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw HttpCore.cancelled(new InterruptedException());
            }
            long remaining = deadline - http.now();
            if (remaining <= 0) {
                throw waitTimeout(address, timeout, lastFailure);
            }
            int serverTimeout = (int) Math.min(MAX_SERVER_WAIT_S, Math.max(1, (remaining + 999) / 1000));
            long started = http.now();
            SimpleMessage message;
            T value;
            try {
                Optional<SimpleMessage> received =
                        messages.waitOnce(address, serverTimeout, after, options.from(), options.subject(), true);
                if (received.isEmpty()) {
                    long pause = Math.min(EARLY_EMPTY_PAUSE_MS, deadline - http.now());
                    if (http.now() - started < EARLY_EMPTY_PAUSE_MS && pause > 0) {
                        http.sleep(pause);
                    }
                    continue;
                }
                message = received.get();
                if (match != null && !match.test(message)) {
                    after = message.id();
                    skipped = message;
                    continue;
                }
                // `after` moves past the message only once it is handled: a transient failure of the extra
                // request brings the same message back.
                value = extract.apply(message, deadline);
            } catch (NotFoundError e) {
                if (skipped != null
                        && skipped.id().equals(after)
                        && AFTER_NOT_FOUND.equals(APIError.stringField(e.body(), "message"))) {
                    // The message the loop skipped was deleted meanwhile: continue after its time instead.
                    after = Times.format(skipped.createdAt());
                    skipped = null;
                    continue;
                }
                throw e;
            } catch (APIError | APIConnectionError e) {
                if (!isTransient(e)) {
                    throw e;
                }
                lastFailure = e;
                long left = deadline - http.now();
                if (left > 0) {
                    Long retryAfter = retryAfterMillis(e);
                    http.sleep(Math.min(retryAfter != null ? retryAfter : TRANSIENT_PAUSE_MS, left));
                }
                continue;
            }
            // Continue strictly after this message whatever happens next: its id is exact, a time is not.
            after = message.id();
            skipped = message;
            if (value != null) {
                return value;
            }
        }
    }

    /** What the wait helpers return for a message, or {@code null} to skip it. */
    @FunctionalInterface
    private interface Extractor<T> {
        T apply(SimpleMessage message, long deadline);
    }

    /** Failures the wait loop waits out: the server is busy or the response was lost, nothing is wrong. */
    private static boolean isTransient(FakeEmailError e) {
        if (e instanceof APIConnectionError) {
            return true; // timeouts included
        }
        int status = ((APIError) e).status();
        return status == 429 || status == 503 || HttpCore.isLostResponse(status);
    }

    private Long retryAfterMillis(FakeEmailError e) {
        if (!(e instanceof APIError)) {
            return null;
        }
        String header = ((APIError) e).headers().firstValue("retry-after").orElse(null);
        return RetryAfter.parseMillis(header, http.now());
    }

    private static WaitTimeoutError waitTimeout(String address, Duration timeout, FakeEmailError cause) {
        WaitTimeoutError error = new WaitTimeoutError(address, timeout);
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }

    /**
     * Releases the HTTP client if this instance created it (on Java 21+, where {@link HttpClient} is {@link
     * AutoCloseable}; on Java 17 its threads end once it is unreachable). An injected client is left open, and
     * a view from {@link #withOptions} closes nothing.
     */
    @Override
    public void close() {
        if (ownsHttpClient && http.http instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Closing is best effort; there is nothing a caller could do about it.
            }
        }
    }

    private static String trimTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    /** Builder of {@link FakeEmail}. */
    public static final class Builder {
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private String userAgent;
        private HttpClient httpClient;
        // Test hooks, package-private: environment, sleep, clock and jitter.
        private Function<String, String> env = System::getenv;
        private HttpCore.Sleeper sleeper = Thread::sleep;
        private LongSupplier clock = System::currentTimeMillis;
        private DoubleSupplier random = () -> ThreadLocalRandom.current().nextDouble();

        private Builder() {}

        /** API key ({@code fep_...}). Defaults to the {@code FAKE_EMAIL_API_KEY} environment variable. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Default {@code https://api.fake-email.pro}; trailing slashes are trimmed, a path prefix is kept. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        /**
         * Timeout of each HTTP attempt (default 30 s). A {@code wait} request gets its server timeout + 10 s
         * instead.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Checks.positive(timeout, "timeout");
            return this;
        }

        /**
         * Retries on 429 and 503 (unless its {@code Retry-After} is over 5 s), and on 502/504/520..524/network
         * errors and timeouts for GET and DELETE (default 2). The wait helpers retry on their own until their
         * deadline instead.
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = Checks.notNegative(maxRetries, "maxRetries");
            return this;
        }

        /** Appended to the SDK's own {@code User-Agent} token, e.g. {@code "my-e2e-suite/1.0"}. */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * The {@link HttpClient} to send requests with (proxy, SSL, executor). Defaults to a new one, which
         * {@link FakeEmail#close()} closes; a client passed here is never closed by the SDK.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
            return this;
        }

        Builder env(Function<String, String> env) {
            this.env = env;
            return this;
        }

        Builder sleeper(HttpCore.Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        Builder clock(LongSupplier clock) {
            this.clock = clock;
            return this;
        }

        Builder random(DoubleSupplier random) {
            this.random = random;
            return this;
        }

        /**
         * @throws FakeEmailError without an API key
         */
        public FakeEmail build() {
            return new FakeEmail(this);
        }
    }
}
