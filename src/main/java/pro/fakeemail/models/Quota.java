package pro.fakeemail.models;

import java.time.Instant;
import java.util.Objects;

/**
 * The monthly quota of Public API mailboxes (calendar month in UTC).
 *
 * @param limit API mailboxes per month on the plan
 * @param used API mailboxes created since the 1st of the current month, deleted ones included
 * @param resetsAt the 1st of the next month, 00:00 UTC
 */
public record Quota(int limit, int used, Instant resetsAt) {
    public Quota {
        Objects.requireNonNull(resetsAt, "resetsAt");
    }
}
