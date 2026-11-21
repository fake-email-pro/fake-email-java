package pro.fakeemail;

import java.net.http.HttpHeaders;

/** 403 {@code plan_forbidden}: the plan has no API access, or does not allow a custom {@code localPart}. */
public class PermissionDeniedError extends APIError {
    private static final long serialVersionUID = 1L;

    public PermissionDeniedError(int status, HttpHeaders headers, Object body) {
        super(status, headers, body);
    }
}
