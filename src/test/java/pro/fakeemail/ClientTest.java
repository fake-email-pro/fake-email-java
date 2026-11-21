package pro.fakeemail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pro.fakeemail.Fixtures.json;
import static pro.fakeemail.Fixtures.mailbox;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClientTest {
    @Nested
    class Construction {
        @Test
        void readsTheKeyFromFakeEmailApiKey() {
            FakeHttpClient http = new FakeHttpClient(new FakeClock(), List.of(json(mailbox())));
            FakeEmail client = FakeEmail.builder()
                    .httpClient(http)
                    .env(name -> name.equals("FAKE_EMAIL_API_KEY") ? "fep_from_env" : null)
                    .build();
            client.mailboxes().get("a@example.org");
            assertEquals("Bearer fep_from_env", http.requests.get(0).header("authorization"));
        }

        @Test
        void prefersAnExplicitKeyOverTheEnvironment() {
            Harness h = Harness.with(b -> b.apiKey("fep_explicit").env(name -> "fep_from_env"), json(mailbox()));
            h.client.mailboxes().get("a@example.org");
            assertEquals("Bearer fep_explicit", h.request(0).header("authorization"));
        }

        @Test
        void throwsWithoutAKey() {
            FakeEmailError e = assertThrows(
                    FakeEmailError.class,
                    () -> FakeEmail.builder().env(name -> null).build());
            assertTrue(e.getMessage().startsWith("missing API key"), e.getMessage());
            assertTrue(e.getMessage().contains("FAKE_EMAIL_API_KEY"));
            assertTrue(e.getMessage().contains("https://fake-email.pro/settings/api-keys"));
            assertThrows(
                    FakeEmailError.class,
                    () -> FakeEmail.builder().env(name -> "").build());
            assertThrows(
                    FakeEmailError.class,
                    () -> FakeEmail.builder().apiKey("").env(name -> "fep_x").build());
        }

        @Test
        void defaultsToTheProductionBaseUrl() {
            FakeHttpClient http = new FakeHttpClient(new FakeClock(), List.of(json(mailbox())));
            FakeEmail client =
                    FakeEmail.builder().apiKey("fep_x").httpClient(http).build();
            assertEquals("https://api.fake-email.pro", client.baseUrl());
            client.mailboxes().get("a@example.org");
            assertEquals(
                    "https://api.fake-email.pro/v1/mailboxes/a%40example.org",
                    http.requests.get(0).uri().toString());
            assertEquals(
                    java.util.Optional.of(Duration.ofSeconds(30)),
                    http.requests.get(0).timeout());
        }

        @Test
        void toleratesTrailingSlashesAndAPathPrefixInBaseUrl() {
            Harness h = Harness.with(b -> b.baseUrl("https://gw.test/fake//"), json(mailbox()));
            assertEquals("https://gw.test/fake", h.client.baseUrl());
            h.client.mailboxes().get("a@example.org");
            assertEquals(
                    "https://gw.test/fake/v1/mailboxes/a%40example.org",
                    h.request(0).uri().toString());
        }

        @Test
        void rejectsInvalidSettings() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FakeEmail.builder().baseUrl("ftp://x").apiKey("k").build());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FakeEmail.builder().baseUrl("not a url").apiKey("k").build());
            assertThrows(
                    IllegalArgumentException.class, () -> FakeEmail.builder().timeout(Duration.ZERO));
            assertThrows(
                    IllegalArgumentException.class, () -> FakeEmail.builder().maxRetries(-1));
            assertThrows(NullPointerException.class, () -> FakeEmail.builder().httpClient(null));
            assertThrows(
                    IllegalArgumentException.class, () -> WaitOptions.builder().timeout(Duration.ofSeconds(-1)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> RequestOptions.builder().maxRetries(-1));
        }

        @Test
        void aDefaultClientOwnsItsHttpClientAndCloses() {
            try (FakeEmail client = FakeEmail.builder().apiKey("fep_x").build()) {
                assertEquals("https://api.fake-email.pro", client.baseUrl());
            }
        }

        @Test
        void anInjectedHttpClientIsNeverClosed() {
            Harness h = Harness.of();
            h.client.withOptions(RequestOptions.none()).close();
            h.client.close();
            assertFalse(h.http.closed);
        }
    }

    @Nested
    class Headers {
        @Test
        void sendsAuthAcceptAndTheSdkUserAgent() {
            Harness h = Harness.of(json(mailbox()));
            h.client.mailboxes().get("a@example.org");
            var r = h.request(0);
            assertEquals("Bearer fep_test", r.header("authorization"));
            assertEquals("application/json", r.header("accept"));
            assertEquals("fake-email-pro-java/" + FakeEmail.VERSION, r.header("user-agent"));
            assertNull(r.header("content-type"));
        }

        @Test
        void appendsACustomUserAgent() {
            Harness h = Harness.with(b -> b.userAgent("my-suite/1.0"), json(mailbox()));
            h.client.mailboxes().get("a@example.org");
            assertEquals(
                    "fake-email-pro-java/" + FakeEmail.VERSION + " my-suite/1.0",
                    h.request(0).header("user-agent"));
        }

        @Test
        void versionMatchesThePom() {
            String expected = System.getProperty("fakeemail.projectVersion");
            assertTrue(expected != null && !expected.isEmpty(), "surefire passes the pom version");
            assertEquals(expected, FakeEmail.VERSION);
        }
    }
}
