package pro.fakeemail;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import pro.fakeemail.models.DetailedMessage;
import pro.fakeemail.models.DownloadedAttachment;
import pro.fakeemail.models.Page;
import pro.fakeemail.models.SimpleMessage;

/** {@code /v1/mailboxes/{address}/messages}. Get it with {@link FakeEmail#messages()}. */
public final class MessagesResource {
    private static final int DEFAULT_SERVER_WAIT_S = 30;
    // The HTTP attempt must outlive the server's hold, or a healthy long poll would be cut off as a timeout.
    private static final Duration WAIT_HTTP_MARGIN = Duration.ofSeconds(10);

    private final HttpCore http;
    private final RequestOptions options;

    MessagesResource(HttpCore http, RequestOptions options) {
        this.http = http;
        this.options = options;
    }

    /** The first page of the mailbox's messages, newest first. To watch for new mail use {@code wait}. */
    public Page<SimpleMessage> list(String address) {
        return list(address, PageParams.builder().build());
    }

    /** One page of the mailbox's messages, newest first. To watch for new mail use {@code wait}. */
    public Page<SimpleMessage> list(String address, PageParams params) {
        Objects.requireNonNull(params, "params");
        HttpCore.Call call = call("GET", base(address) + "/messages")
                .query("page", params.page())
                .query("size", params.size());
        return Decode.page(http.json(call), Decode::simpleMessage);
    }

    /** Every message of the mailbox, newest first, fetching pages of 50 lazily. */
    public PageIterable<SimpleMessage> listAll(String address) {
        return listAll(address, 50);
    }

    /** Every message of the mailbox, newest first, fetching pages of {@code pageSize} (1..50) lazily. */
    public PageIterable<SimpleMessage> listAll(String address, int pageSize) {
        Objects.requireNonNull(address, "address");
        return new PageIterable<>(page -> list(address, PageParams.of(page, pageSize)));
    }

    /**
     * One long poll with the server's default timeout (30 s): the earliest message in the mailbox, or empty
     * when none arrived in time. For a loop with a client-side deadline, use {@link FakeEmail#waitForMessage}.
     */
    public Optional<SimpleMessage> wait(String address) {
        return wait(address, WaitParams.builder().build());
    }

    /**
     * One long poll: the earliest matching message (strictly after {@code after}), or empty when the server's
     * timeout passed without one. For a loop with a client-side deadline, use {@link FakeEmail#waitForMessage}.
     */
    public Optional<SimpleMessage> wait(String address, WaitParams params) {
        Objects.requireNonNull(params, "params");
        return waitOnce(address, params.timeoutSeconds(), params.after(), params.from(), params.subject(), null);
    }

    Optional<SimpleMessage> waitOnce(
            String address, Integer timeoutSeconds, String after, String from, String subject, Long deadline) {
        int serverTimeout = timeoutSeconds != null ? timeoutSeconds : DEFAULT_SERVER_WAIT_S;
        HttpCore.Call call = new HttpCore.Call("GET", base(address) + "/messages/wait")
                .query("timeout", timeoutSeconds)
                .query("after", after)
                .query("from", from)
                .query("subject", subject);
        call.timeout = Duration.ofSeconds(serverTimeout).plus(WAIT_HTTP_MARGIN);
        call.options(options);
        call.deadline = deadline;
        Object json = http.jsonOrNull(call);
        return json == null ? Optional.empty() : Optional.of(Decode.simpleMessage(json));
    }

    /** The full message: bodies, attachments, every code and link. */
    public DetailedMessage get(String address, String id) {
        return Decode.detailedMessage(http.json(call("GET", messagePath(address, id))));
    }

    /** Deletes the message. */
    public void delete(String address, String id) {
        http.send(call("DELETE", messagePath(address, id)));
    }

    /** The raw EML, byte for byte. */
    public byte[] raw(String address, String id) {
        return http.send(call("GET", messagePath(address, id) + "/raw")).body();
    }

    /** The decoded bytes of an attachment, with its type and file name. */
    public DownloadedAttachment attachment(String address, String id, String attachmentId) {
        String path = messagePath(address, id) + "/attachments/"
                + HttpCore.seg(Objects.requireNonNull(attachmentId, "attachmentId"));
        HttpResponse<byte[]> response = http.send(call("GET", path));
        return new DownloadedAttachment(
                response.body(),
                response.headers().firstValue("content-type").orElse("application/octet-stream"),
                ContentDisposition.fileName(
                        response.headers().firstValue("content-disposition").orElse(null)));
    }

    private HttpCore.Call call(String method, String path) {
        return new HttpCore.Call(method, path).options(options);
    }

    private static String base(String address) {
        return MailboxesResource.path(address);
    }

    private static String messagePath(String address, String id) {
        return base(address) + "/messages/" + HttpCore.seg(Objects.requireNonNull(id, "id"));
    }
}
