package pro.fakeemail;

import java.net.http.HttpHeaders;
import java.util.Map;

/**
 * The server answered with a non-2xx status. Subclasses tell the statuses apart; any other status is a plain
 * {@code APIError}.
 *
 * <p>The message is {@code "<status> <code>: <message>"}, or {@code "<status>: HTTP <status>"} when the body
 * is not the API's JSON envelope (e.g. a proxy's 502 page).
 */
public class APIError extends FakeEmailError {
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final Object body;
    private final HttpHeaders headers;
    private final RateLimitInfo rateLimit;

    public APIError(int status, HttpHeaders headers, Object body) {
        super(message(status, body));
        this.status = status;
        this.code = stringField(body, "error");
        this.body = body;
        this.headers = headers;
        this.rateLimit = parseRateLimit(headers);
    }

    /** HTTP status. */
    public int status() {
        return status;
    }

    /**
     * The {@code error} field of the body (see {@link ErrorCode}); {@code null} when the body is not the JSON
     * envelope.
     */
    public String code() {
        return code;
    }

    /**
     * The parsed JSON body ({@code Map<String, Object>}, {@code List<Object>}, ...), or the raw text when it is
     * not JSON, or {@code null} when it is empty.
     */
    public Object body() {
        return body;
    }

    /** Response headers. */
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * From {@code X-RateLimit-*}; {@code null} when the response did not carry them (the state is unknown, not
     * zero).
     */
    public RateLimitInfo rateLimit() {
        return rateLimit;
    }

    static APIError fromResponse(int status, HttpHeaders headers, Object body, long nowMillis) {
        switch (status) {
            case 400:
                return new BadRequestError(status, headers, body);
            case 401:
                return new AuthenticationError(status, headers, body);
            case 402:
                return new QuotaExhaustedError(status, headers, body);
            case 403:
                return new PermissionDeniedError(status, headers, body);
            case 404:
                return new NotFoundError(status, headers, body);
            case 409:
                return new ConflictError(status, headers, body);
            case 429:
                return new RateLimitError(status, headers, body, nowMillis);
            default:
                return status >= 500
                        ? new InternalServerError(status, headers, body)
                        : new APIError(status, headers, body);
        }
    }

    private static String message(int status, Object body) {
        String error = stringField(body, "error");
        String message = stringField(body, "message");
        String phrase = message != null ? message : "HTTP " + status;
        return error != null && !error.isEmpty() ? status + " " + error + ": " + phrase : status + ": " + phrase;
    }

    static String stringField(Object body, String name) {
        if (body instanceof Map) {
            Object value = ((Map<?, ?>) body).get(name);
            return value instanceof String ? (String) value : null;
        }
        return null;
    }

    static Long longField(Object body, String name) {
        if (body instanceof Map) {
            Object value = ((Map<?, ?>) body).get(name);
            if (value instanceof Long) {
                return (Long) value;
            }
            if (value instanceof Double && Double.isFinite((Double) value)) {
                return Math.round((Double) value);
            }
        }
        return null;
    }

    private static RateLimitInfo parseRateLimit(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String limit = headers.firstValue("x-ratelimit-limit").orElse(null);
        String remaining = headers.firstValue("x-ratelimit-remaining").orElse(null);
        String reset = headers.firstValue("x-ratelimit-reset").orElse(null);
        if (limit == null || remaining == null || reset == null) {
            return null;
        }
        try {
            return new RateLimitInfo(
                    Long.parseLong(limit.trim()), Long.parseLong(remaining.trim()), Long.parseLong(reset.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
