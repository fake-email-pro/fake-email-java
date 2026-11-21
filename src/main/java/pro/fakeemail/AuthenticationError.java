package pro.fakeemail;

import java.net.http.HttpHeaders;

/** 401 {@code unauthorized}: the key is missing, unknown, revoked or expired. */
public class AuthenticationError extends APIError {
    private static final long serialVersionUID = 1L;

    public AuthenticationError(int status, HttpHeaders headers, Object body) {
        super(status, headers, body);
    }
}
