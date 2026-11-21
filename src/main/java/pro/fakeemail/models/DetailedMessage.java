package pro.fakeemail.models;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The full message. Attachments are in the order of the message's parts.
 *
 * @param id message id (UUID)
 * @param createdAt delivery time
 * @param from the sender; {@code null} for {@code UNPARSED} and for a message without {@code From}
 * @param subject subject; {@code null} if there is none or the message was not parsed
 * @param bodyState how much of the message is stored
 * @param html the HTML part; {@code null} if there is none, and for {@code UNPARSED}/{@code OVERFLOW}
 * @param text the plain-text part (never derived from the HTML); {@code null} if there is none
 * @param attachments attachments
 * @param codes one-time codes, computed on read; the first is the best guess
 * @param links links ranked by how likely they are to "confirm / sign in"; the first is the best guess
 */
public record DetailedMessage(
        String id,
        Instant createdAt,
        Sender from,
        String subject,
        BodyState bodyState,
        String html,
        String text,
        List<Attachment> attachments,
        List<String> codes,
        List<Link> links) {
    public DetailedMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(bodyState, "bodyState");
        attachments = List.copyOf(attachments);
        codes = List.copyOf(codes);
        links = List.copyOf(links);
    }
}
