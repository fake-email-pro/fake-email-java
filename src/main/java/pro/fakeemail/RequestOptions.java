package pro.fakeemail;

import java.time.Duration;

/**
 * Per-call overrides of the client's settings, applied with {@link FakeEmail#withOptions(RequestOptions)}:
 *
 * <pre>{@code
 * mail.withOptions(RequestOptions.builder().maxRetries(0).build()).domains().list();
 * }</pre>
 */
public final class RequestOptions {
    private static final RequestOptions NONE = new RequestOptions(null, null);

    private final Duration timeout;
    private final Integer maxRetries;

    private RequestOptions(Duration timeout, Integer maxRetries) {
        this.timeout = timeout;
        this.maxRetries = maxRetries;
    }

    /** No overrides. */
    public static RequestOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Timeout of each HTTP attempt, or {@code null} for the client's. A {@code wait} request always gets at least
     * its server timeout + 10 s: this timeout applies to it only when it is longer.
     */
    public Duration timeout() {
        return timeout;
    }

    /** Retries, or {@code null} for the client's. */
    public Integer maxRetries() {
        return maxRetries;
    }

    /** These options with the ones set in {@code other} taking precedence. */
    RequestOptions merge(RequestOptions other) {
        return new RequestOptions(
                other.timeout != null ? other.timeout : timeout,
                other.maxRetries != null ? other.maxRetries : maxRetries);
    }

    @Override
    public String toString() {
        return "RequestOptions[timeout=" + timeout + ", maxRetries=" + maxRetries + "]";
    }

    /** Builder of {@link RequestOptions}. */
    public static final class Builder {
        private Duration timeout;
        private Integer maxRetries;

        private Builder() {}

        /** Timeout of each HTTP attempt of the call; a {@code wait} request keeps at least server timeout + 10 s. */
        public Builder timeout(Duration timeout) {
            this.timeout = Checks.positive(timeout, "timeout");
            return this;
        }

        /** Retries of the call (0 turns them off). */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = Checks.notNegative(maxRetries, "maxRetries");
            return this;
        }

        public RequestOptions build() {
            return new RequestOptions(timeout, maxRetries);
        }
    }
}
