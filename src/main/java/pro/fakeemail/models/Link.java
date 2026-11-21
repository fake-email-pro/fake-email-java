package pro.fakeemail.models;

import java.util.Objects;

/**
 * A link found in a message.
 *
 * @param url {@code http://} or {@code https://} URL
 * @param text visible link text (or the image's {@code alt}); {@code null} for bare URLs
 */
public record Link(String url, String text) {
    public Link {
        Objects.requireNonNull(url, "url");
    }
}
