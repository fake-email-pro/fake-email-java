package pro.fakeemail.models;

import java.util.Arrays;
import java.util.Objects;

/**
 * The decoded content of an attachment.
 *
 * @param data the bytes (a copy is returned on each access)
 * @param contentType the response's {@code Content-Type}, {@code application/octet-stream} if absent
 * @param fileName from {@code Content-Disposition} ({@code filename*} preferred); {@code null} if absent
 */
public record DownloadedAttachment(byte[] data, String contentType, String fileName) {
    public DownloadedAttachment {
        data = data.clone();
        Objects.requireNonNull(contentType, "contentType");
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DownloadedAttachment
                && Arrays.equals(data, ((DownloadedAttachment) o).data)
                && contentType.equals(((DownloadedAttachment) o).contentType)
                && Objects.equals(fileName, ((DownloadedAttachment) o).fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(data), contentType, fileName);
    }

    @Override
    public String toString() {
        return "DownloadedAttachment[data=" + data.length + " bytes, contentType=" + contentType + ", fileName="
                + fileName + "]";
    }
}
