package pro.fakeemail;

import java.net.http.HttpHeaders;

/** 5xx, including a proxy's 502 during a server restart and 503 {@code service unavailable}. */
public class InternalServerError extends APIError {
    private static final long serialVersionUID = 1L;

    public InternalServerError(int status, HttpHeaders headers, Object body) {
        super(status, headers, body);
    }
}
