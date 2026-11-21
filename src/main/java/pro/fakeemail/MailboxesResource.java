package pro.fakeemail;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import pro.fakeemail.models.Mailbox;
import pro.fakeemail.models.Page;

/** {@code /v1/mailboxes}: create, list, get and delete mailboxes. Get it with {@link FakeEmail#mailboxes()}. */
public final class MailboxesResource {
    private final HttpCore http;
    private final RequestOptions options;

    MailboxesResource(HttpCore http, RequestOptions options) {
        this.http = http;
        this.options = options;
    }

    /** Creates a mailbox with a random address on a random domain. Spends one unit of the monthly quota. */
    public Mailbox create() {
        return create(CreateMailboxParams.builder().build());
    }

    /**
     * Creates a mailbox: a random address on {@code params.domain()}, or exactly {@code localPart@domain}.
     * Spends one unit of the monthly quota.
     */
    public Mailbox create(CreateMailboxParams params) {
        Objects.requireNonNull(params, "params");
        Map<String, Object> body = new LinkedHashMap<>();
        if (params.localPart() != null) {
            body.put("localPart", params.localPart());
        }
        if (params.domain() != null) {
            body.put("domain", params.domain());
        }
        return Decode.mailbox(http.json(call("POST", "/v1/mailboxes").body(body)));
    }

    /** The first page of active mailboxes, newest first. */
    public Page<Mailbox> list() {
        return list(PageParams.builder().build());
    }

    /** One page of active mailboxes, newest first. */
    public Page<Mailbox> list(PageParams params) {
        Objects.requireNonNull(params, "params");
        HttpCore.Call call =
                call("GET", "/v1/mailboxes").query("page", params.page()).query("size", params.size());
        return Decode.page(http.json(call), Decode::mailbox);
    }

    /** Every active mailbox, fetching pages of 50 lazily as iteration goes. */
    public PageIterable<Mailbox> listAll() {
        return listAll(50);
    }

    /** Every active mailbox, fetching pages of {@code pageSize} (1..50) lazily as iteration goes. */
    public PageIterable<Mailbox> listAll(int pageSize) {
        return new PageIterable<>(page -> list(PageParams.of(page, pageSize)));
    }

    /** The mailbox; {@link NotFoundError} once it expired or was deleted. */
    public Mailbox get(String address) {
        return Decode.mailbox(http.json(call("GET", path(address))));
    }

    /** Deletes the mailbox and its messages. The address is never issued again; the quota is not given back. */
    public void delete(String address) {
        http.send(call("DELETE", path(address)));
    }

    private HttpCore.Call call(String method, String path) {
        return new HttpCore.Call(method, path).options(options);
    }

    static String path(String address) {
        return "/v1/mailboxes/" + HttpCore.seg(Objects.requireNonNull(address, "address"));
    }
}
