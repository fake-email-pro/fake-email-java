package pro.fakeemail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pro.fakeemail.Fixtures.ADDR;
import static pro.fakeemail.Fixtures.ENC;
import static pro.fakeemail.Fixtures.apiError;
import static pro.fakeemail.Fixtures.arr;
import static pro.fakeemail.Fixtures.detailedMessage;
import static pro.fakeemail.Fixtures.empty;
import static pro.fakeemail.Fixtures.hold;
import static pro.fakeemail.Fixtures.json;
import static pro.fakeemail.Fixtures.obj;
import static pro.fakeemail.Fixtures.simpleMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pro.fakeemail.models.SimpleMessage;

class WaitTest {
    private static List<String> timeouts(Harness h) {
        return h.requests().stream().map(r -> r.query().get("timeout")).collect(Collectors.toList());
    }

    private static WaitOptions.Builder opts() {
        return WaitOptions.builder();
    }

    @Nested
    class WaitForMessage {
        @Test
        void returnsTheFirstMessageAndPassesTheFilters() {
            Harness h = Harness.of(json(simpleMessage()));
            SimpleMessage res = h.client.waitForMessage(
                    ADDR,
                    opts().from("acme")
                            .subject("code")
                            .timeout(Duration.ofSeconds(30))
                            .build());
            assertEquals(Decode.simpleMessage(Json.parse(Json.write(simpleMessage()))), res);
            assertEquals(
                    Map.of("timeout", "30", "from", "acme", "subject", "code"),
                    h.request(0).query());
        }

        @Test
        void defaultsToSixtySeconds() {
            Harness h = Harness.of(json(simpleMessage()));
            h.client.waitForMessage(ADDR);
            assertEquals(Map.of("timeout", "60"), h.request(0).query());
            assertEquals(
                    java.util.Optional.of(Duration.ofSeconds(70)), h.request(0).timeout());
        }

        @Test
        void loopsOn204WithTimeoutMinOfRemainingAnd60Seconds() {
            Harness h = Harness.of(hold(), hold(), json(simpleMessage()));
            SimpleMessage res = h.client.waitForMessage(
                    ADDR, opts().timeout(Duration.ofSeconds(150)).build());
            assertEquals("048213", res.code());
            assertEquals(List.of("60", "60", "30"), timeouts(h));
            assertEquals(List.of(), h.sleeps(), "a 204 after a full hold is not followed by a pause");
        }

        @Test
        void throwsWaitTimeoutErrorAtTheDeadline() {
            Harness h = Harness.of(hold(), hold());
            WaitTimeoutError err = assertThrows(
                    WaitTimeoutError.class,
                    () -> h.client.waitForMessage(
                            ADDR, opts().timeout(Duration.ofMillis(90_500)).build()));
            assertEquals(ADDR, err.address());
            assertEquals(Duration.ofMillis(90_500), err.timeout());
            assertEquals("no matching message in " + ADDR + " within 90500 ms", err.getMessage());
            assertEquals(List.of("60", "31"), timeouts(h));
        }

        @Test
        void passesAfterAsAnIdOrAnInstantOnTheFirstCall() {
            Harness h = Harness.of(json(simpleMessage()), json(simpleMessage()));
            h.client.waitForMessage(
                    ADDR, opts().after("3c1b5f0e-6a8e-4b0e-9d3e-1f2a7c9b8e41").build());
            assertEquals(
                    "3c1b5f0e-6a8e-4b0e-9d3e-1f2a7c9b8e41", h.request(0).query().get("after"));
            h.client.waitForMessage(
                    ADDR, opts().after(Instant.parse("2026-10-22T09:00:00Z")).build());
            assertEquals("2026-10-22T09:00:00.000Z", h.request(1).query().get("after"));
        }

        @Test
        void skipsMessagesFailingMatchAndContinuesAfterThem() {
            Harness h = Harness.of(
                    json(simpleMessage("id", "m1", "subject", "Welcome")),
                    json(simpleMessage("id", "m2", "subject", "Reset your password")));
            SimpleMessage res = h.client.waitForMessage(
                    ADDR,
                    opts().match(m -> m.subject() != null && m.subject().contains("password"))
                            .build());
            assertEquals("m2", res.id());
            assertNull(h.request(0).query().get("after"));
            assertEquals("m1", h.request(1).query().get("after"));
        }

        @Test
        void afterAdvancesEvenWhenStartedWithATime() {
            Harness h = Harness.of(json(simpleMessage("id", "m1")), json(simpleMessage("id", "m2")));
            h.client.waitForMessage(
                    ADDR,
                    opts().after(Instant.parse("2026-10-22T09:00:00Z"))
                            .match(m -> m.id().equals("m2"))
                            .build());
            assertEquals("2026-10-22T09:00:00.000Z", h.request(0).query().get("after"));
            assertEquals("m1", h.request(1).query().get("after"));
        }

        @Test
        void pausesInsteadOfSpinningWhenA204ComesBackEarly() {
            Harness h = Harness.of(empty(204), json(simpleMessage()));
            h.client.waitForMessage(ADDR);
            assertEquals(2, h.requests().size());
            assertEquals(List.of(1000L), h.sleeps());
        }

        @Test
        void theEarlyPauseNeverPassesTheDeadline() {
            Harness h = Harness.of((req, clock) -> {
                clock.advance(500);
                return FakeHttpClient.response(204, null);
            });
            assertThrows(
                    WaitTimeoutError.class,
                    () -> h.client.waitForMessage(
                            ADDR, opts().timeout(Duration.ofMillis(800)).build()));
            assertEquals(List.of(300L), h.sleeps());
            assertEquals(1, h.requests().size());
        }

        @Test
        void waitsOutTooManyWaitsBeyondMaxRetriesWhileTheDeadlineAllows() {
            var tooMany = apiError(429, "too_many_waits", "too many concurrent waits", "Retry-After", "1");
            Harness h = Harness.of(tooMany, tooMany, tooMany, tooMany, json(simpleMessage()));
            SimpleMessage res = h.client.waitForMessage(ADDR);
            assertEquals("5d2e8a10-7b3c-4f1e-8a2d-9c4b6e1f3a57", res.id());
            assertEquals(5, h.requests().size());
            // Two retries inside the request, then the loop's own pause, then one more retry.
            assertEquals(List.of(1000L, 1000L, 1000L, 1000L), h.sleeps());
        }

        @Test
        void aRateLimitWithoutRetryAfterIsWaitedOutForOneSecond() {
            var limited = apiError(429, "too_many_waits", "too many concurrent waits");
            Harness h = Harness.with(b -> b.maxRetries(0), limited, json(simpleMessage()));
            h.client.waitForMessage(ADDR);
            assertEquals(List.of(1000L), h.sleeps());
        }

        @Test
        void givesUpOnA429WhoseRetryAfterPassesTheDeadline() {
            Harness h = Harness.of(apiError(429, "rate_limit_exceeded", "rate limit exceeded", "Retry-After", "50"));
            assertThrows(
                    RateLimitError.class,
                    () -> h.client.waitForMessage(
                            ADDR, opts().timeout(Duration.ofSeconds(10)).build()));
            assertEquals(1, h.requests().size());
            assertEquals(List.of(), h.sleeps());
        }

        @Test
        void doesNotRetryPastTheDeadline() {
            // 502 on GET is retryable, but the backoff (500 ms) would end after the deadline (300 ms away).
            Harness h = Harness.of((req, clock) -> {
                clock.advance(700);
                return FakeHttpClient.response(502, "bad gateway");
            });
            assertThrows(
                    InternalServerError.class,
                    () -> h.client.waitForMessage(
                            ADDR, opts().timeout(Duration.ofSeconds(1)).build()));
            assertEquals(1, h.requests().size());
        }

        @Test
        void doesNotSwallowA404() {
            Harness h = Harness.of(apiError(404, "not_found", "mailbox not found or expired"));
            assertThrows(NotFoundError.class, () -> h.client.waitForMessage(ADDR));
        }

        @Test
        void stopsWhenTheThreadIsInterrupted() {
            Harness h = Harness.of(
                    (req, clock) -> {
                        Thread.currentThread().interrupt();
                        clock.advance(30_000);
                        return FakeHttpClient.response(204, null);
                    },
                    json(simpleMessage()));
            try {
                assertThrows(CancellationException.class, () -> h.client.waitForMessage(ADDR));
                assertEquals(1, h.requests().size());
            } finally {
                Thread.interrupted();
            }
        }

        @Test
        void throwsAtOnceOnAnAlreadyInterruptedThread() {
            Harness h = Harness.of(json(simpleMessage()));
            Thread.currentThread().interrupt();
            try {
                assertThrows(CancellationException.class, () -> h.client.waitForMessage(ADDR));
                assertEquals(0, h.requests().size());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Nested
    class WaitForCode {
        @Test
        void returnsTheCodeComputedOnDelivery() {
            Harness h = Harness.of(json(simpleMessage("code", "550712")));
            assertEquals("550712", h.client.waitForCode(ADDR));
            assertEquals(1, h.requests().size());
        }

        @Test
        void fallsBackToTheFirstCodeOfTheFullMessage() {
            Harness h = Harness.of(
                    json(simpleMessage("id", "m1", "code", null)),
                    json(detailedMessage("id", "m1", "codes", arr("9911", "1234"))));
            assertEquals("9911", h.client.waitForCode(ADDR));
            assertEquals("/v1/mailboxes/" + ENC + "/messages/m1", h.request(1).rawPath());
        }

        @Test
        void keepsWaitingAfterAMessageWithoutAnyCode() {
            Harness h = Harness.of(
                    json(simpleMessage("id", "m1", "code", null)),
                    json(detailedMessage("id", "m1", "codes", arr())),
                    json(simpleMessage("id", "m2", "code", "777888")));
            assertEquals(
                    "777888", h.client.waitForCode(ADDR, opts().subject("code").build()));
            assertEquals("m1", h.request(2).query().get("after"));
            assertEquals("code", h.request(2).query().get("subject"));
        }

        @Test
        void appliesMatchBeforeExtracting() {
            Harness h = Harness.of(
                    json(simpleMessage(
                            "id", "m1", "code", "111111", "from", obj("name", null, "address", "a@other.example"))),
                    json(simpleMessage("id", "m2", "code", "222222")));
            String code = h.client.waitForCode(
                    ADDR,
                    opts().match(m -> m.from() != null && m.from().address().equals("no-reply@acme.example"))
                            .build());
            assertEquals("222222", code);
            assertEquals(2, h.requests().size());
        }
    }

    @Nested
    class WaitForLink {
        @Test
        void returnsConfirmLink() {
            Harness h = Harness.of(json(simpleMessage("confirmLink", "https://acme.example/v?t=1")));
            assertEquals("https://acme.example/v?t=1", h.client.waitForLink(ADDR));
        }

        @Test
        void fallsBackToTheFirstLinkThenKeepsWaiting() {
            Harness h = Harness.of(
                    json(simpleMessage("id", "m1", "confirmLink", null)),
                    json(detailedMessage("id", "m1", "links", arr())),
                    json(simpleMessage("id", "m2", "confirmLink", null)),
                    json(detailedMessage(
                            "id", "m2", "links", arr(obj("url", "https://acme.example/magic", "text", "Sign in")))));
            assertEquals("https://acme.example/magic", h.client.waitForLink(ADDR));
            assertEquals("m1", h.request(2).query().get("after"));
            assertTrue(h.http.remaining() == 0);
        }
    }
}
