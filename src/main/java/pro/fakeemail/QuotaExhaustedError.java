package pro.fakeemail;

import java.net.http.HttpHeaders;
import java.time.Instant;

/**
 * 402 {@code quota_exhausted}: the monthly mailbox quota is spent. Not retried: it renews at {@link
 * #resetsAt()}.
 */
public class QuotaExhaustedError extends APIError {
    private static final long serialVersionUID = 1L;

    private final Integer limit;
    private final Integer used;
    private final Instant resetsAt;
    private final String upgradeUrl;

    public QuotaExhaustedError(int status, HttpHeaders headers, Object body) {
        super(status, headers, body);
        this.limit = toInteger(longField(body, "limit"));
        this.used = toInteger(longField(body, "used"));
        this.resetsAt = parseInstant(stringField(body, "resetsAt"));
        this.upgradeUrl = stringField(body, "upgradeUrl");
    }

    /** Mailboxes per month on the plan; {@code null} if the body did not say. */
    public Integer limit() {
        return limit;
    }

    /** Mailboxes created this month; {@code null} if the body did not say. */
    public Integer used() {
        return used;
    }

    /** The 1st of next month 00:00 UTC; {@code null} if the body did not say. */
    public Instant resetsAt() {
        return resetsAt;
    }

    /** The pricing page; {@code null} if the body did not say. */
    public String upgradeUrl() {
        return upgradeUrl;
    }

    private static Integer toInteger(Long value) {
        return value == null || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE ? null : value.intValue();
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Times.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
