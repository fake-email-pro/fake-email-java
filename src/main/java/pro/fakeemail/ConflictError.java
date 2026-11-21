package pro.fakeemail;

import java.net.http.HttpHeaders;

/** 409 {@code address_taken}: the address was issued before, to anyone. */
public class ConflictError extends APIError {
    private static final long serialVersionUID = 1L;

    public ConflictError(int status, HttpHeaders headers, Object body) {
        super(status, headers, body);
    }
}
