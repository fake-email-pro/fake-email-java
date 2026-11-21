package pro.fakeemail.models;

import java.util.Objects;

/**
 * The numbers that apply to the caller right now, under their current plan.
 *
 * @param quota the monthly quota of API mailboxes
 * @param requestsPerMinute rate limit, requests per minute per user
 * @param concurrentWaits {@code wait} requests held open at once per user
 * @param storage storage taken by the account's messages
 * @param mailboxTtlSeconds how long a mailbox accepts mail after creation
 */
public record Limits(Quota quota, int requestsPerMinute, int concurrentWaits, Storage storage, long mailboxTtlSeconds) {
    public Limits {
        Objects.requireNonNull(quota, "quota");
        Objects.requireNonNull(storage, "storage");
    }
}
