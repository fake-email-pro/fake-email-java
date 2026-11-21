package pro.fakeemail;

import java.net.http.HttpHeaders;

/** 400 {@code bad_request}: a parameter or the body is invalid. */
public class BadRequestError extends APIError {
    private static final long serialVersionUID = 1L;

    public BadRequestError(int status, HttpHeaders headers, Object body) {
        super(status, headers, body);
    }
}
