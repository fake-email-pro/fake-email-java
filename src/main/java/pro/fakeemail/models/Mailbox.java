package pro.fakeemail.models;

import java.time.Instant;
import java.util.Objects;

/**
 * An active API mailbox. It accepts mail for 24 hours after creation, then is deleted together with its
 * messages.
 *
 * @param address {@code localPart@domain} in lowercase
 * @param activeSince creation time
 * @param expiresAt when the mailbox stops accepting mail; from then on it and its messages are 404
 * @param storageUsedBytes the sum of {@code sizeBytes} of the mailbox's messages
 * @param messagesCount number of messages in the mailbox
 */
public record Mailbox(
        String address, Instant activeSince, Instant expiresAt, long storageUsedBytes, int messagesCount) {
    public Mailbox {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(activeSince, "activeSince");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
