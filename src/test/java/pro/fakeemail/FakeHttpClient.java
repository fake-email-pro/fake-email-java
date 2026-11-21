package pro.fakeemail;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/** An {@link HttpClient} that answers from a queue of replies and records every request. No network. */
final class FakeHttpClient extends HttpClient {
    /** What a request was answered with: a response, or an exception from {@code send}. */
    @FunctionalInterface
    interface Reply {
        HttpResponse<byte[]> answer(Recorded request, FakeClock clock) throws IOException, InterruptedException;
    }

    /** A request as the SDK sent it. */
    record Recorded(String method, URI uri, HttpHeaders headers, byte[] body, Optional<Duration> timeout) {
        String rawPath() {
            return uri.getRawPath();
        }

        String rawQuery() {
            return uri.getRawQuery();
        }

        /** Decoded query parameters in order. */
        Map<String, String> query() {
            Map<String, String> out = new LinkedHashMap<>();
            if (uri.getRawQuery() == null) {
                return out;
            }
            for (String pair : uri.getRawQuery().split("&")) {
                int eq = pair.indexOf('=');
                out.put(
                        URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
            return out;
        }

        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }

        Object json() {
            return body == null ? null : Json.parse(new String(body, StandardCharsets.UTF_8));
        }
    }

    private final Deque<Reply> replies;
    private final FakeClock clock;
    final List<Recorded> requests = new ArrayList<>();
    boolean closed;

    FakeHttpClient(FakeClock clock, List<Reply> replies) {
        this.clock = clock;
        this.replies = new ArrayDeque<>(replies);
    }

    int remaining() {
        return replies.size();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        Recorded recorded =
                new Recorded(request.method(), request.uri(), request.headers(), readBody(request), request.timeout());
        requests.add(recorded);
        Reply reply = replies.poll();
        if (reply == null) {
            throw new AssertionError("unexpected request " + request.method() + " " + request.uri());
        }
        HttpResponse<byte[]> response = reply.answer(recorded, clock);
        return (HttpResponse<T>) new Response(response.statusCode(), response.headers(), response.body(), request);
    }

    private static byte[] readBody(HttpRequest request) {
        Optional<HttpRequest.BodyPublisher> publisher = request.bodyPublisher();
        if (publisher.isEmpty() || publisher.get().contentLength() == 0) {
            return null;
        }
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        publisher.get().subscribe(new Flow.Subscriber<ByteBuffer>() {
            private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                out.write(chunk, 0, chunk.length);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                result.complete(out.toByteArray());
            }
        });
        try {
            return result.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("could not read the request body", e);
        }
    }

    /** Called on Java 21+ through AutoCloseable; on Java 17 it is just a method. */
    public void close() {
        closed = true;
    }

    // ---- replies ----

    static HttpResponse<byte[]> response(int status, String body, String... headers) {
        return new Response(
                status, headers(headers), body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8), null);
    }

    static HttpResponse<byte[]> bytes(int status, byte[] body, String... headers) {
        return new Response(status, headers(headers), body, null);
    }

    static HttpHeaders headers(String... nameValues) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < nameValues.length; i += 2) {
            map.computeIfAbsent(nameValues[i], k -> new ArrayList<>()).add(nameValues[i + 1]);
        }
        return HttpHeaders.of(map, (a, b) -> true);
    }

    private static final class Response implements HttpResponse<byte[]> {
        private final int status;
        private final HttpHeaders headers;
        private final byte[] body;
        private final HttpRequest request;

        Response(int status, HttpHeaders headers, byte[] body, HttpRequest request) {
            this.status = status;
            this.headers = headers;
            this.body = body;
            this.request = request;
        }

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<byte[]>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public byte[] body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request == null ? null : request.uri();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }

    // ---- the rest of HttpClient, unused ----

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> handler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
        return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SSLParameters sslParameters() {
        return new SSLParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return Optional.empty();
    }

    @Override
    public Version version() {
        return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
        return Optional.empty();
    }
}
