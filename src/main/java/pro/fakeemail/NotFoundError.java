package pro.fakeemail;

import java.net.http.HttpHeaders;

/**
 * 404 {@code not_found}: the mailbox is not an active API mailbox of yours, the message or attachment is not there,
 * the {@code domain} of a create is unknown or unavailable, or the {@code after} of a wait names a message that is
 * not in the mailbox.
 */
public class NotFoundError extends APIError {
    private static final long serialVersionUID = 1L;

    public NotFoundError(int status, HttpHeaders headers, Object body) {
        super(status, headers, body);
    }
}
