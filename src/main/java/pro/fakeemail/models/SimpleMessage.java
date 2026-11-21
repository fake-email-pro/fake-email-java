package pro.fakeemail.models;

import java.time.Instant;
import java.util.Objects;

/**
 * A message in a list and in the {@code wait} response.
 *
 * @param id message id (UUID)
 * @param mailboxAddress address of the message's mailbox
 * @param createdAt delivery time; do not use it as {@code after}
 * @param sizeBytes EML size; {@code 0} for {@code OVERFLOW}
 * @param from the sender; {@code null} for {@code UNPARSED} and for a message without {@code From}
 * @param subject subject; {@code null} if there is none or the message was not parsed, never {@code ""}
 * @param bodyState how much of the message is stored
 * @param attachmentsCount number of attachments
 * @param code the best one-time code, computed on delivery; {@code null} if none
 * @param confirmLink the best "confirm / sign in" link, computed on delivery; {@code null} if none
 */
public record SimpleMessage(
        String id,
        String mailboxAddress,
        Instant createdAt,
        long sizeBytes,
        Sender from,
        String subject,
        BodyState bodyState,
        int attachmentsCount,
        String code,
        String confirmLink) {
    public SimpleMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mailboxAddress, "mailboxAddress");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(bodyState, "bodyState");
    }
}
