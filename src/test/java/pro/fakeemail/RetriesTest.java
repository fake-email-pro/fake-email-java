package pro.fakeemail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pro.fakeemail.Fixtures.apiError;
import static pro.fakeemail.Fixtures.arr;
import static pro.fakeemail.Fixtures.empty;
import static pro.fakeemail.Fixtures.json;
import static pro.fakeemail.Fixtures.mailbox;
import static pro.fakeemail.Fixtures.obj;
import static pro.fakeemail.Fixtures.text;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RetriesTest {
    private static FakeHttpClient.Reply busy() {
        return apiError(503, "internal_server_error", "service unavailable", "Retry-After", "1");
    }

    private static FakeHttpClient.Reply limited(String retryAfter) {
        return apiError(429, "rate_limit_exceeded", "rate limit exceeded", "Retry-After", retryAfter);
    }

    private static FakeHttpClient.Reply badGateway() {
        return text(502, "bad gateway");
    }

    private static FakeHttpClient.Reply domains() {
        return json(obj("domains", arr()));
    }

    private static FakeHttpClient.Reply networkError() {
        return Fixtures.fail(new IOException("connection reset"));
    }

    @Test
    void retries429HonoringRetryAfter() {
        Harness h = Harness.of(limited("7"), domains());
        assertEquals(List.of(), h.client.domains().list());
        assertEquals(2, h.requests().size());
        assertEquals(List.of(7000L), h.sleeps());
    }

    @Test
    void acceptsRetryAfterAsAnHttpDate() {
        String at = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                Instant.ofEpochMilli(1_800_000_000_000L + 3000).atOffset(ZoneOffset.UTC));
        Harness h = Harness.of(limited(at), domains());
        h.client.domains().list();
        assertEquals(List.of(3000L), h.sleeps());
    }

    @Test
    void anUnreadableRetryAfterFallsBackToBackoff() {
        Harness h = Harness.of(limited("soon"), domains());
        h.client.domains().list();
        assertEquals(List.of(500L), h.sleeps());
    }

    @Test
    void capsRetryAfterAt60Seconds() {
        Harness h = Harness.of(limited("3600"), domains());
        h.client.domains().list();
        assertEquals(List.of(60_000L), h.sleeps());
    }

    @Test
    void retries429And503OnPostToo() {
        Harness h = Harness.of(limited("1"), busy(), json(201, mailbox()));
        assertEquals(EndpointsTest.MAILBOX, h.client.mailboxes().create());
        assertEquals(
                List.of("POST", "POST", "POST"),
                h.requests().stream().map(FakeHttpClient.Recorded::method).collect(Collectors.toList()));
    }

    @Test
    void givesUpAfterMaxRetriesAndThrowsTheLastError() {
        Harness h = Harness.of(limited("1"), limited("1"), limited("1"));
        assertThrows(RateLimitError.class, () -> h.client.domains().list());
        assertEquals(3, h.requests().size());
    }

    @Test
    void respectsAPerCallMaxRetries() {
        Harness h = Harness.of(limited("1"));
        FakeEmail noRetries =
                h.client.withOptions(RequestOptions.builder().maxRetries(0).build());
        assertThrows(RateLimitError.class, () -> noRetries.domains().list());
        assertEquals(1, h.requests().size());
    }

    @Test
    void withOptionsKeepsEarlierOverrides() {
        Harness h = Harness.of(limited("1"));
        FakeEmail derived = h.client
                .withOptions(RequestOptions.builder().maxRetries(0).build())
                .withOptions(RequestOptions.builder()
                        .timeout(java.time.Duration.ofSeconds(3))
                        .build());
        assertThrows(RateLimitError.class, () -> derived.domains().list());
        assertEquals(1, h.requests().size());
        assertEquals(
                java.util.Optional.of(java.time.Duration.ofSeconds(3)),
                h.request(0).timeout());
    }

    @Test
    void doesNotRetryPostOn502() {
        Harness h = Harness.of(badGateway(), json(201, mailbox()));
        assertThrows(InternalServerError.class, () -> h.client.mailboxes().create());
        assertEquals(1, h.requests().size());
    }

    @Test
    void doesNotRetryPostOnAConnectionError() {
        Harness h = Harness.of(networkError(), json(201, mailbox()));
        assertThrows(APIConnectionError.class, () -> h.client.mailboxes().create());
        assertEquals(1, h.requests().size());
    }

    @Test
    void retriesGetOn502And504WithExponentialBackoff() {
        Harness h = Harness.with(b -> b.maxRetries(3), badGateway(), text(504, ""), badGateway(), domains());
        h.client.domains().list();
        assertEquals(List.of(500L, 1000L, 2000L), h.sleeps());
    }

    @Test
    void backoffIsJitteredDownwardAndCappedAt8Seconds() {
        List<FakeHttpClient.Reply> replies = new ArrayList<>(Collections.nCopies(7, badGateway()));
        replies.add(domains());
        Harness h =
                Harness.with(b -> b.maxRetries(7).random(() -> 0.999), replies.toArray(new FakeHttpClient.Reply[0]));
        h.client.domains().list();
        List<Long> sleeps = h.sleeps();
        assertEquals(7, sleeps.size());
        for (int i = 0; i < sleeps.size(); i++) {
            assertTrue(sleeps.get(i) <= Math.min(500L << i, 8000), "sleep " + i + " = " + sleeps.get(i));
        }
        assertTrue(sleeps.get(6) >= 6000);
        assertTrue(Collections.max(sleeps) <= 8000);
    }

    @Test
    void retriesDeleteOnAConnectionError() {
        Harness h = Harness.of(networkError(), empty(204));
        h.client.mailboxes().delete("a@example.org");
        assertEquals(
                List.of("DELETE", "DELETE"),
                h.requests().stream().map(FakeHttpClient.Recorded::method).collect(Collectors.toList()));
    }

    @Test
    void doesNotRetry500Or4xx() {
        Harness h = Harness.of(apiError(500, "internal_server_error", "internal server error"));
        assertThrows(InternalServerError.class, () -> h.client.domains().list());
        assertEquals(1, h.requests().size());
    }

    @Test
    void anHttpAttemptOverTheTimeoutIsAnApiTimeoutError() {
        HttpTimeoutException cause = new HttpTimeoutException("request timed out");
        Harness h = Harness.with(b -> b.timeout(java.time.Duration.ofMillis(20)).maxRetries(0), Fixtures.fail(cause));
        APITimeoutError e =
                assertThrows(APITimeoutError.class, () -> h.client.domains().list());
        assertInstanceOf(APIConnectionError.class, e);
        assertSame(cause, e.getCause());
        assertEquals(
                java.util.Optional.of(java.time.Duration.ofMillis(20)),
                h.request(0).timeout());
        assertEquals("request timed out after 20 ms", e.getMessage());
    }

    @Test
    void timeoutsAreRetriedForGet() {
        Harness h = Harness.of(Fixtures.fail(new HttpTimeoutException("timed out")), domains());
        h.client.domains().list();
        assertEquals(2, h.requests().size());
    }

    @Test
    void anInterruptCancelsTheCallAndIsNotRetried() {
        Harness h = Harness.of(
                (req, clock) -> {
                    throw new InterruptedException();
                },
                domains());
        try {
            CancellationException e = assertThrows(
                    CancellationException.class, () -> h.client.domains().list());
            assertInstanceOf(InterruptedException.class, e.getCause());
            assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag is restored");
            assertEquals(1, h.requests().size());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void anAlreadyInterruptedThreadSendsNothing() {
        Harness h = Harness.of(domains());
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> h.client.domains().list());
            assertEquals(0, h.requests().size());
        } finally {
            Thread.interrupted();
        }
    }
}
