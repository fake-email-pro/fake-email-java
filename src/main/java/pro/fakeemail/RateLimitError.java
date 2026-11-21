package pro.fakeemail;

import java.net.http.HttpHeaders;
import java.time.Duration;

/**
 * 429: {@link #code()} is {@code rate_limit_exceeded} (requests per minute) or {@code too_many_waits}
 * (concurrent waits).
 */
public class RateLimitError extends APIError {
    private static final long serialVersionUID = 1L;

    private final Duration retryAfter;

    public RateLimitError(int status, HttpHeaders headers, Object body) {
        this(status, headers, body, System.currentTimeMillis());
    }

    RateLimitError(int status, HttpHeaders headers, Object body, long nowMillis) {
        super(status, headers, body);
        Long ms = headers == null
                ? null
                : RetryAfter.parseMillis(headers.firstValue("retry-after").orElse(null), nowMillis);
        this.retryAfter = ms == null ? null : Duration.ofSeconds((ms + 999) / 1000);
    }

    /** From {@code Retry-After}, in whole seconds (rounded up); {@code null} if absent. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
