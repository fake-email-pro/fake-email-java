package pro.fakeemail;

/**
 * The rate limit window from the {@code X-RateLimit-*} headers.
 *
 * @param limit requests per minute on the plan
 * @param remaining requests left in the current window
 * @param reset end of the window, Unix seconds
 */
public record RateLimitInfo(long limit, long remaining, long reset) {}
