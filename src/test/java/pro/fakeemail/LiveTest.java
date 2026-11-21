package pro.fakeemail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pro.fakeemail.models.Domain;
import pro.fakeemail.models.Limits;
import pro.fakeemail.models.Mailbox;

/**
 * Smoke test against a real server. Skipped unless FAKE_EMAIL_LIVE=1 and FAKE_EMAIL_API_KEY are set;
 * FAKE_EMAIL_BASE_URL points it at another environment. Spends one mailbox of the monthly quota.
 */
@EnabledIfEnvironmentVariable(named = "FAKE_EMAIL_LIVE", matches = "1")
@EnabledIfEnvironmentVariable(named = "FAKE_EMAIL_API_KEY", matches = ".+")
class LiveTest {
    @Test
    void createsReadsAndDeletesAMailbox() {
        FakeEmail.Builder builder = FakeEmail.builder().userAgent("fake-email-pro-java-live-test");
        String baseUrl = System.getenv("FAKE_EMAIL_BASE_URL");
        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }
        try (FakeEmail client = builder.build()) {
            List<Domain> domains = client.domains().list();
            assertFalse(domains.isEmpty(), "no domains to create a mailbox on");

            Limits limits = client.limits().get();
            assertTrue(limits.requestsPerMinute() > 0);

            Mailbox mailbox = client.mailboxes().create();
            try {
                assertTrue(mailbox.address().contains("@"));
                assertEquals(
                        mailbox.address(),
                        client.mailboxes().get(mailbox.address()).address());
                assertEquals(0, client.messages().list(mailbox.address()).totalElements());
                assertEquals(
                        Optional.empty(),
                        client.messages()
                                .wait(
                                        mailbox.address(),
                                        WaitParams.builder()
                                                .timeout(Duration.ofSeconds(1))
                                                .build()));
            } finally {
                client.mailboxes().delete(mailbox.address());
            }
            assertThrows(NotFoundError.class, () -> client.mailboxes().get(mailbox.address()));
        }
    }
}
