package pro.fakeemail;

import java.net.http.HttpHeaders;

/** 404 {@code not_found}: the mailbox is not an active API mailbox of yours, or the message/attachment is not there. */
public class NotFoundError extends APIError {
    private static final long serialVersionUID = 1L;

    public NotFoundError(int status, HttpHeaders headers, Object body) {
        super(status, headers, body);
    }
}
