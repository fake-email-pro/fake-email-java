package pro.fakeemail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Response bodies and replies shared by the tests (the JS suite's helpers.ts). */
final class Fixtures {
    static final String ADDR = "ci-runner-7@example.org";
    static final String ENC = "ci-runner-7%40example.org";
    static final String MID = "3c1b5f0e-6a8e-4b0e-9d3e-1f2a7c9b8e41";
    static final String AID = "9a7e2c44-0d0b-4f7a-a1c1-2b9d5f3e6a10";

    private Fixtures() {}

    /** An ordered JSON object that allows null values: {@code obj("a", 1, "b", null)}. */
    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    /** {@code base} with some fields replaced or added. */
    static Map<String, Object> with(Map<String, Object> base, Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>(base);
        map.putAll(obj(kv));
        return map;
    }

    static List<Object> arr(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    static Map<String, Object> mailbox() {
        return obj(
                "address",
                ADDR,
                "activeSince",
                "2026-10-22T09:00:00.481Z",
                "expiresAt",
                "2026-10-23T09:00:00.481Z",
                "storageUsedBytes",
                18422,
                "messagesCount",
                3);
    }

    static Map<String, Object> simpleMessage(Object... overrides) {
        return with(
                obj(
                        "id", "5d2e8a10-7b3c-4f1e-8a2d-9c4b6e1f3a57",
                        "mailboxAddress", ADDR,
                        "createdAt", "2026-10-22T09:06:40.102Z",
                        "sizeBytes", 9120,
                        "from", obj("name", "Acme", "address", "no-reply@acme.example"),
                        "subject", "Your verification code",
                        "bodyState", "STORED",
                        "attachmentsCount", 0,
                        "code", "048213",
                        "confirmLink", "https://acme.example/verify?t=3f9a"),
                overrides);
    }

    static Map<String, Object> detailedMessage(Object... overrides) {
        return with(
                obj(
                        "id",
                        "5d2e8a10-7b3c-4f1e-8a2d-9c4b6e1f3a57",
                        "createdAt",
                        "2026-10-22T09:06:40.102Z",
                        "from",
                        obj("name", null, "address", "sender@example.com"),
                        "subject",
                        "Welcome",
                        "bodyState",
                        "STORED",
                        "html",
                        "<p>hi</p>",
                        "text",
                        null,
                        "attachments",
                        arr(),
                        "codes",
                        arr(),
                        "links",
                        arr()),
                overrides);
    }

    static Map<String, Object> page(List<?> content, int page, int totalPages) {
        return obj(
                "content",
                content,
                "page",
                page,
                "size",
                50,
                "totalPages",
                totalPages,
                "totalElements",
                content.size());
    }

    static Map<String, Object> limits() {
        return obj(
                "quota", obj("limit", 20, "used", 3, "resetsAt", "2026-11-01T00:00:00.000Z"),
                "requestsPerMinute", 60,
                "concurrentWaits", 10,
                "storage", obj("usedBytes", 18422, "limitBytes", 52428800),
                "mailboxTtlSeconds", 86400);
    }

    // ---- replies ----

    static FakeHttpClient.Reply json(Object body) {
        return json(200, body);
    }

    static FakeHttpClient.Reply json(int status, Object body, String... headers) {
        String[] all = new String[headers.length + 2];
        all[0] = "Content-Type";
        all[1] = "application/json";
        System.arraycopy(headers, 0, all, 2, headers.length);
        return (req, clock) -> FakeHttpClient.response(status, Json.write(body), all);
    }

    static FakeHttpClient.Reply empty(int status, String... headers) {
        return (req, clock) -> FakeHttpClient.response(status, null, headers);
    }

    static FakeHttpClient.Reply text(int status, String body, String... headers) {
        return (req, clock) -> FakeHttpClient.response(status, body, headers);
    }

    static FakeHttpClient.Reply apiError(int status, String error, String message, String... headers) {
        return json(status, obj("error", error, "message", message), headers);
    }

    static FakeHttpClient.Reply fail(IOException e) {
        return (req, clock) -> {
            throw e;
        };
    }

    static FakeHttpClient.Reply bytes(int status, byte[] body, String... headers) {
        return (req, clock) -> FakeHttpClient.bytes(status, body, headers);
    }

    /** A wait reply that "holds" for the requested server timeout on the virtual clock, then answers 204. */
    static FakeHttpClient.Reply hold() {
        return (req, clock) -> {
            String timeout = req.query().getOrDefault("timeout", "30");
            clock.advance(Long.parseLong(timeout) * 1000);
            return FakeHttpClient.response(204, null);
        };
    }
}
