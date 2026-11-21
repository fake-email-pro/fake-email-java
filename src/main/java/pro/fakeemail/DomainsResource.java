package pro.fakeemail;

import java.util.List;
import pro.fakeemail.models.Domain;

/** {@code /v1/domains}. Get it with {@link FakeEmail#domains()}. */
public final class DomainsResource {
    private final HttpCore http;
    private final RequestOptions options;

    DomainsResource(HttpCore http, RequestOptions options) {
        this.http = http;
        this.options = options;
    }

    /** Domains you can create a mailbox on, sorted by name. */
    public List<Domain> list() {
        return Decode.domains(http.json(new HttpCore.Call("GET", "/v1/domains").options(options)));
    }
}
