package pro.fakeemail;

/**
 * Values of {@link APIError#code()} (the {@code error} field of the API's error body). The list is shared by
 * every API of the site; the Public API returns a subset, and a newer server may return a value not listed
 * here, which is why {@code code()} is a plain string.
 *
 * <pre>{@code
 * catch (RateLimitError e) {
 *     if (ErrorCode.TOO_MANY_WAITS.equals(e.code())) { ... }
 * }
 * }</pre>
 */
public final class ErrorCode {
    public static final String BAD_REQUEST = "bad_request";
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String QUOTA_EXHAUSTED = "quota_exhausted";
    public static final String FORBIDDEN = "forbidden";
    public static final String OAUTH_REQUIRED = "oauth_required";
    public static final String PLAN_FORBIDDEN = "plan_forbidden";
    public static final String PLAN_LIMIT_EXCEEDED = "plan_limit_exceeded";
    public static final String NOT_FOUND = "not_found";
    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";
    public static final String ADDRESS_TAKEN = "address_taken";
    public static final String DOMAIN_UNAVAILABLE = "domain_unavailable";
    public static final String CONFIRMATION_REQUIRED = "confirmation_required";
    public static final String INVALID_STATE = "invalid_state";
    public static final String DOMAIN_EXISTS = "domain_exists";
    public static final String PAYLOAD_TOO_LARGE = "payload_too_large";
    public static final String UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type";
    public static final String RATE_LIMIT_EXCEEDED = "rate_limit_exceeded";
    public static final String TOO_MANY_WAITS = "too_many_waits";
    public static final String INTERNAL_SERVER_ERROR = "internal_server_error";

    private ErrorCode() {}
}
