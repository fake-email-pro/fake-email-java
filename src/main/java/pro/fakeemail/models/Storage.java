package pro.fakeemail.models;

/**
 * Storage taken by the account's messages (per user, across all mailboxes).
 *
 * @param usedBytes bytes used
 * @param limitBytes the plan's limit
 */
public record Storage(long usedBytes, long limitBytes) {}
