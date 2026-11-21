package pro.fakeemail;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pro.fakeemail.Fixtures.ADDR;
import static pro.fakeemail.Fixtures.AID;
import static pro.fakeemail.Fixtures.ENC;
import static pro.fakeemail.Fixtures.MID;
import static pro.fakeemail.Fixtures.arr;
import static pro.fakeemail.Fixtures.bytes;
import static pro.fakeemail.Fixtures.detailedMessage;
import static pro.fakeemail.Fixtures.empty;
import static pro.fakeemail.Fixtures.json;
import static pro.fakeemail.Fixtures.mailbox;
import static pro.fakeemail.Fixtures.obj;
import static pro.fakeemail.Fixtures.page;
import static pro.fakeemail.Fixtures.simpleMessage;
import static pro.fakeemail.Fixtures.with;

import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pro.fakeemail.models.Attachment;
import pro.fakeemail.models.BodyState;
import pro.fakeemail.models.DetailedMessage;
import pro.fakeemail.models.Domain;
import pro.fakeemail.models.DomainType;
import pro.fakeemail.models.DownloadedAttachment;
import pro.fakeemail.models.Limits;
import pro.fakeemail.models.Link;
import pro.fakeemail.models.Mailbox;
import pro.fakeemail.models.Page;
import pro.fakeemail.models.Quota;
import pro.fakeemail.models.Sender;
import pro.fakeemail.models.SimpleMessage;
import pro.fakeemail.models.Storage;

class EndpointsTest {
    static final Mailbox MAILBOX = new Mailbox(
            ADDR, Instant.parse("2026-10-22T09:00:00.481Z"), Instant.parse("2026-10-23T09:00:00.481Z"), 18422, 3);

    @Nested
    class Mailboxes {
        @Test
        void createSendsPostWithTheGivenFields() {
            Harness h = Harness.of(json(201, mailbox()));
            Mailbox mb = h.client
                    .mailboxes()
                    .create(CreateMailboxParams.builder()
                            .localPart("ci-runner-7")
                            .domain("example.org")
                            .build());
            assertEquals(MAILBOX, mb);
            var r = h.request(0);
            assertEquals("POST", r.method());
            assertEquals("/v1/mailboxes", r.rawPath());
            assertNull(r.rawQuery());
            assertEquals(obj("localPart", "ci-runner-7", "domain", "example.org"), r.json());
            assertEquals("application/json", r.header("content-type"));
        }

        @Test
        void createWithNoParamsSendsAnEmptyObject() {
            Harness h = Harness.of(json(201, mailbox()));
            h.client.mailboxes().create();
            assertEquals(Map.of(), h.request(0).json());
            assertEquals("{}", new String(h.request(0).body(), StandardCharsets.UTF_8));
        }

        @Test
        void createOnADomainSendsOnlyTheDomain() {
            Harness h = Harness.of(json(201, mailbox()));
            h.client
                    .mailboxes()
                    .create(CreateMailboxParams.builder().domain("example.org").build());
            assertEquals(obj("domain", "example.org"), h.request(0).json());
        }

        @Test
        void listPassesPageAndSizeAndOmitsUnsetOnes() {
            Harness h = Harness.of(json(page(arr(mailbox()), 0, 1)), json(page(arr(), 0, 0)));
            Page<Mailbox> res = h.client.mailboxes().list(PageParams.of(2, 10));
            assertEquals(List.of(MAILBOX), res.content());
            assertEquals(0, res.page());
            assertEquals(50, res.size());
            assertEquals(1, res.totalPages());
            assertEquals(1, res.totalElements());
            assertFalse(res.hasNextPage());
            assertEquals("GET", h.request(0).method());
            assertEquals("/v1/mailboxes", h.request(0).rawPath());
            assertEquals("page=2&size=10", h.request(0).rawQuery());
            h.client.mailboxes().list();
            assertNull(h.request(1).rawQuery());
            assertNull(h.request(1).body());
        }

        @Test
        void listWithOnlyASize() {
            Harness h = Harness.of(json(page(arr(), 0, 0)));
            h.client.mailboxes().list(PageParams.builder().size(5).build());
            assertEquals("size=5", h.request(0).rawQuery());
        }

        @Test
        void listAllWalksEveryPageLazily() {
            var a = with(mailbox(), "address", "a@example.org");
            var b = with(mailbox(), "address", "b@example.org");
            var c = with(mailbox(), "address", "c@example.org");
            Harness h = Harness.of(json(page(arr(a, b), 0, 2)), json(page(arr(c), 1, 2)));
            PageIterable<Mailbox> all = h.client.mailboxes().listAll();
            assertEquals(0, h.requests().size(), "nothing is fetched before iterating");
            List<String> addresses = new ArrayList<>();
            for (Mailbox m : all) {
                addresses.add(m.address());
            }
            assertEquals(List.of("a@example.org", "b@example.org", "c@example.org"), addresses);
            assertEquals(
                    List.of("page=0&size=50", "page=1&size=50"),
                    h.requests().stream().map(FakeHttpClient.Recorded::rawQuery).collect(Collectors.toList()));
        }

        @Test
        void listAllStreamFetchesOnlyWhatIsConsumed() {
            var a = with(mailbox(), "address", "a@example.org");
            Harness h = Harness.of(json(page(arr(a), 0, 5)));
            Optional<Mailbox> first = h.client.mailboxes().listAll().stream().findFirst();
            assertEquals("a@example.org", first.orElseThrow().address());
            assertEquals(1, h.requests().size());
        }

        @Test
        void listAllStopsOnAnEmptyFirstPage() {
            Harness h = Harness.of(json(page(arr(), 0, 0)));
            List<Mailbox> all = h.client.mailboxes().listAll(5).stream().collect(Collectors.toList());
            assertEquals(List.of(), all);
            assertEquals(1, h.requests().size());
            assertEquals("5", h.request(0).query().get("size"));
        }

        @Test
        void getEncodesTheAddress() {
            Harness h = Harness.of(json(mailbox()));
            assertEquals(MAILBOX, h.client.mailboxes().get(ADDR));
            assertEquals("GET", h.request(0).method());
            assertEquals("/v1/mailboxes/" + ENC, h.request(0).rawPath());
        }

        @Test
        void getEncodesCharactersThatWouldBreakThePath() {
            Harness h = Harness.of(json(mailbox()));
            h.client.mailboxes().get("a/b?c#d@example.org");
            assertEquals(
                    "/v1/mailboxes/a%2Fb%3Fc%23d%40example.org", h.request(0).rawPath());
            assertNull(h.request(0).rawQuery());
        }

        @Test
        void deleteSendsDeleteAndReturnsOn204() {
            Harness h = Harness.of(empty(204));
            h.client.mailboxes().delete(ADDR);
            assertEquals("DELETE", h.request(0).method());
            assertEquals("/v1/mailboxes/" + ENC, h.request(0).rawPath());
            assertNull(h.request(0).body());
            assertNull(h.request(0).header("content-type"));
        }

        @Test
        void nullArgumentsAreRejectedBeforeAnyRequest() {
            Harness h = Harness.of();
            assertThrows(NullPointerException.class, () -> h.client.mailboxes().get(null));
            assertThrows(NullPointerException.class, () -> h.client.mailboxes().create(null));
            assertThrows(NullPointerException.class, () -> h.client.messages().get(ADDR, null));
            assertEquals(0, h.requests().size());
        }
    }

    @Nested
    class Messages {
        @Test
        void list() {
            Harness h = Harness.of(json(page(arr(simpleMessage()), 0, 1)));
            Page<SimpleMessage> res = h.client.messages().list(ADDR, PageParams.of(0, 5));
            assertEquals("048213", res.content().get(0).code());
            assertEquals("/v1/mailboxes/" + ENC + "/messages", h.request(0).rawPath());
            assertEquals("page=0&size=5", h.request(0).rawQuery());
        }

        @Test
        void listAll() {
            Harness h = Harness.of(
                    json(page(arr(simpleMessage("id", "1")), 0, 2)), json(page(arr(simpleMessage("id", "2")), 1, 2)));
            List<String> ids = new ArrayList<>();
            for (SimpleMessage m : h.client.messages().listAll(ADDR)) {
                ids.add(m.id());
            }
            assertEquals(List.of("1", "2"), ids);
            assertEquals("/v1/mailboxes/" + ENC + "/messages", h.request(1).rawPath());
            assertEquals("page=1&size=50", h.request(1).rawQuery());
        }

        @Test
        void waitSendsEveryFilterAndReturnsTheMessage() {
            Harness h = Harness.of(json(simpleMessage(
                    "id", MID, "createdAt", "2026-10-22T09:05:12.481Z", "sizeBytes", 18422, "code", "550712")));
            Optional<SimpleMessage> res = h.client
                    .messages()
                    .wait(
                            ADDR,
                            WaitParams.builder()
                                    .timeout(Duration.ofSeconds(45))
                                    .after(MID)
                                    .from("acme")
                                    .subject("verification code")
                                    .build());
            assertEquals(
                    Optional.of(new SimpleMessage(
                            MID,
                            ADDR,
                            Instant.parse("2026-10-22T09:05:12.481Z"),
                            18422,
                            new Sender("Acme", "no-reply@acme.example"),
                            "Your verification code",
                            BodyState.STORED,
                            0,
                            "550712",
                            "https://acme.example/verify?t=3f9a")),
                    res);
            var r = h.request(0);
            assertEquals("GET", r.method());
            assertEquals("/v1/mailboxes/" + ENC + "/messages/wait", r.rawPath());
            assertEquals(
                    Map.of("timeout", "45", "after", MID, "from", "acme", "subject", "verification code"), r.query());
            assertEquals("timeout=45&after=" + MID + "&from=acme&subject=verification+code", r.rawQuery());
        }

        @Test
        void waitReturnsEmptyOn204() {
            Harness h = Harness.of(empty(204));
            assertEquals(Optional.empty(), h.client.messages().wait(ADDR));
            assertNull(h.request(0).rawQuery());
        }

        @Test
        void waitTurnsAnInstantIntoIsoWithMillisecondsAndEncodesOffsets() {
            Harness h = Harness.of(empty(204), empty(204), empty(204));
            Instant at = OffsetDateTime.of(2026, 10, 22, 9, 0, 0, 5_000_000, ZoneOffset.UTC)
                    .toInstant();
            h.client.messages().wait(ADDR, WaitParams.builder().after(at).build());
            assertEquals("2026-10-22T09:00:00.005Z", h.request(0).query().get("after"));
            h.client
                    .messages()
                    .wait(
                            ADDR,
                            WaitParams.builder()
                                    .after("2026-10-22T12:00:00+03:00")
                                    .build());
            assertTrue(h.request(1).rawQuery().contains("after=2026-10-22T12%3A00%3A00%2B03%3A00"));
            // Microseconds are truncated to milliseconds, like Date.toISOString.
            h.client
                    .messages()
                    .wait(
                            ADDR,
                            WaitParams.builder()
                                    .after(Instant.parse("2026-10-22T12:00:00.123456Z"))
                                    .build());
            assertEquals("2026-10-22T12:00:00.123Z", h.request(2).query().get("after"));
        }

        @Test
        void waitRoundsTheServerTimeoutUpToWholeSeconds() {
            Harness h = Harness.of(empty(204));
            h.client
                    .messages()
                    .wait(
                            ADDR,
                            WaitParams.builder()
                                    .timeout(Duration.ofMillis(1500))
                                    .build());
            assertEquals("2", h.request(0).query().get("timeout"));
        }

        @Test
        void waitGivesTheHttpAttemptTenSecondsMoreThanTheServerTimeout() {
            Harness h = Harness.with(b -> b.maxRetries(0), empty(204), empty(204), (req, clock) -> {
                throw new HttpTimeoutException("request timed out");
            });
            h.client
                    .messages()
                    .wait(
                            ADDR,
                            WaitParams.builder().timeout(Duration.ofSeconds(1)).build());
            assertEquals(Optional.of(Duration.ofSeconds(11)), h.request(0).timeout());
            h.client.messages().wait(ADDR);
            assertEquals(Optional.of(Duration.ofSeconds(40)), h.request(1).timeout());
            APITimeoutError err = assertThrows(
                    APITimeoutError.class, () -> h.client.messages().wait(ADDR));
            assertEquals("request timed out after 40000 ms", err.getMessage());
        }

        @Test
        void aPerCallTimeoutOverridesTheWaitMargin() {
            Harness h = Harness.of(empty(204));
            h.client
                    .withOptions(RequestOptions.builder()
                            .timeout(Duration.ofSeconds(5))
                            .build())
                    .messages()
                    .wait(ADDR);
            assertEquals(Optional.of(Duration.ofSeconds(5)), h.request(0).timeout());
        }

        @Test
        void getReturnsTheFullMessage() {
            Harness h = Harness.of(json(detailedMessage(
                    "id", MID,
                    "text", null,
                    "codes", arr("048213"),
                    "links", arr(obj("url", "https://click.example.net/ls/click?upn=u001.abc", "text", "Verify email")),
                    "attachments",
                            arr(obj(
                                    "id",
                                    AID,
                                    "contentType",
                                    "application/pdf",
                                    "fileName",
                                    "invoice.pdf",
                                    "sizeBytes",
                                    51234)))));
            DetailedMessage full = h.client.messages().get(ADDR, MID);
            assertEquals(
                    new DetailedMessage(
                            MID,
                            Instant.parse("2026-10-22T09:06:40.102Z"),
                            new Sender(null, "sender@example.com"),
                            "Welcome",
                            BodyState.STORED,
                            "<p>hi</p>",
                            null,
                            List.of(new Attachment(AID, "application/pdf", "invoice.pdf", 51234)),
                            List.of("048213"),
                            List.of(new Link("https://click.example.net/ls/click?upn=u001.abc", "Verify email"))),
                    full);
            assertEquals(
                    "/v1/mailboxes/" + ENC + "/messages/" + MID, h.request(0).rawPath());
        }

        @Test
        void delete() {
            Harness h = Harness.of(empty(204));
            h.client.messages().delete(ADDR, MID);
            assertEquals("DELETE", h.request(0).method());
            assertEquals(
                    "/v1/mailboxes/" + ENC + "/messages/" + MID, h.request(0).rawPath());
        }

        @Test
        void rawReturnsTheEmlBytes() {
            byte[] eml = "Received: by fake-email\r\nSubject: hi\r\n\r\nbody".getBytes(StandardCharsets.UTF_8);
            Harness h = Harness.of(bytes(
                    200,
                    eml,
                    "Content-Type",
                    "message/rfc822",
                    "Content-Disposition",
                    "attachment; filename=\"" + MID + ".eml\""));
            assertArrayEquals(eml, h.client.messages().raw(ADDR, MID));
            assertEquals(
                    "/v1/mailboxes/" + ENC + "/messages/" + MID + "/raw",
                    h.request(0).rawPath());
        }

        @Test
        void attachmentReturnsBytesTypeAndTheRfc5987FileName() {
            Harness h = Harness.of(bytes(
                    200,
                    new byte[] {1, 2, 3},
                    "Content-Type",
                    "application/pdf",
                    "Content-Disposition",
                    "attachment; filename=\"Rechnung ?.pdf\"; filename*=UTF-8''Rechnung%20%E2%82%AC.pdf"));
            DownloadedAttachment att = h.client.messages().attachment(ADDR, MID, AID);
            assertEquals(new DownloadedAttachment(new byte[] {1, 2, 3}, "application/pdf", "Rechnung €.pdf"), att);
            assertArrayEquals(new byte[] {1, 2, 3}, att.data());
            assertEquals(
                    "/v1/mailboxes/" + ENC + "/messages/" + MID + "/attachments/" + AID,
                    h.request(0).rawPath());
        }

        @Test
        void attachmentWithoutHeadersFallsBack() {
            Harness h = Harness.of(bytes(200, new byte[] {9}));
            DownloadedAttachment att = h.client.messages().attachment(ADDR, MID, AID);
            assertEquals("application/octet-stream", att.contentType());
            assertNull(att.fileName());
        }
    }

    @Nested
    class DomainsAndLimits {
        @Test
        void domainsListUnwrapsTheList() {
            Harness h = Harness.of(json(obj(
                    "domains",
                    arr(obj("name", "example.net", "type", "PREMIUM"), obj("name", "example.org", "type", "PUBLIC")))));
            assertEquals(
                    List.of(
                            new Domain("example.net", DomainType.PREMIUM),
                            new Domain("example.org", DomainType.PUBLIC)),
                    h.client.domains().list());
            assertEquals("GET", h.request(0).method());
            assertEquals("/v1/domains", h.request(0).rawPath());
        }

        @Test
        void limitsGet() {
            Harness h = Harness.of(json(Fixtures.limits()));
            Limits limits = h.client.limits().get();
            assertEquals(
                    new Limits(
                            new Quota(20, 3, Instant.parse("2026-11-01T00:00:00Z")),
                            60,
                            10,
                            new Storage(18422, 52428800),
                            86400),
                    limits);
            assertEquals("GET", h.request(0).method());
            assertEquals("/v1/limits", h.request(0).rawPath());
        }

        @Test
        void aMalformedSuccessBodyIsAFakeEmailError() {
            Harness h = Harness.of(Fixtures.text(200, "<html>oops</html>"), json(obj("domains", "nope")));
            FakeEmailError err =
                    assertThrows(FakeEmailError.class, () -> h.client.limits().get());
            assertTrue(err.getMessage().startsWith("the API answered with invalid JSON"), err.getMessage());
            err = assertThrows(FakeEmailError.class, () -> h.client.domains().list());
            assertEquals("unexpected API response: Domains.domains is not an array", err.getMessage());
            assertInstanceOf(FakeEmailError.class, err);
        }
    }
}
