package pro.fakeemail.models;

import java.util.Objects;

/**
 * An attachment of a {@link DetailedMessage}. Download it with {@code client.messages().attachment(...)}.
 *
 * @param id attachment id (UUID)
 * @param contentType MIME type as declared by the sender, without parameters
 * @param fileName file name as declared by the sender; {@code null} if there is none
 * @param sizeBytes size of the decoded content
 */
public record Attachment(String id, String contentType, String fileName, long sizeBytes) {
    public Attachment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contentType, "contentType");
    }
}
