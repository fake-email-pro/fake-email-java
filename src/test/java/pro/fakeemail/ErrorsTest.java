package pro.fakeemail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static pro.fakeemail.Fixtures.apiError;
import static pro.fakeemail.Fixtures.empty;
import static pro.fakeemail.Fixtures.json;
import static pro.fakeemail.Fixtures.obj;
import static pro.fakeemail.Fixtures.text;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ErrorsTest {
    private static final String[] RL = {
        "X-RateLimit-Limit", "60", "X-RateLimit-Remaining", "0", "X-RateLimit-Reset", "1792659660"
    };

    private static FakeEmailError failWith(FakeHttpClient.Reply reply) {
        Harness h = Harness.with(b -> b.maxRetries(0), reply);
        return assertThrows(FakeEmailError.class, () -> h.client.mailboxes().create());
    }

    static Stream<Arguments> mapping() {
        return Stream.of(
                Arguments.of(400, "bad_request", "invalid localPart", BadRequestError.class),
                Arguments.of(401, "unauthorized", "missing or invalid API key", AuthenticationError.class),
                Arguments.of(
                        403, "plan_forbidden", "plan does not allow custom localPart", PermissionDeniedError.class),
                Arguments.of(404, "not_found", "domain not found", NotFoundError.class),
                Arguments.of(409, "address_taken", "address taken", ConflictError.class),
                Arguments.of(500, "internal_server_error", "internal server error", InternalServerError.class),
                Arguments.of(503, "internal_server_error", "service unavailable", InternalServerError.class),
                Arguments.of(405, "method_not_allowed", "method not allowed", APIError.class),
                Arguments.of(413, "payload_too_large", "request body too large", APIError.class),
                Arguments.of(415, "unsupported_media_type", "unsupported media type", APIError.class));
    }

    @ParameterizedTest(name = "{0} {1} -> {3}")
    @MethodSource("mapping")
    void mapsStatusToClass(int status, String code, String message, Class<?> cls) {
        FakeEmailError err = failWith(apiError(status, code, message));
        assertEquals(cls, err.getClass());
        APIError e = assertInstanceOf(APIError.class, err);
        assertInstanceOf(RuntimeException.class, err);
        assertEquals(status, e.status());
        assertEquals(code, e.code());
        assertEquals(status + " " + code + ": " + message, e.getMessage());
        assertEquals(obj("error", code, "message", message), e.body());
    }

    @Test
    void quotaExhaustedCarriesTheQuotaFields() {
        FakeEmailError err = failWith(json(
                402,
                obj(
                        "error", "quota_exhausted",
                        "message", "monthly mailbox quota exhausted",
                        "limit", 20,
                        "used", 20,
                        "resetsAt", "2026-11-01T00:00:00.000Z",
                        "upgradeUrl", "https://fake-email.pro/pricing"),
                RL));
        QuotaExhaustedError e = assertInstanceOf(QuotaExhaustedError.class, err);
        assertEquals("quota_exhausted", e.code());
        assertEquals(20, e.limit());
        assertEquals(20, e.used());
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), e.resetsAt());
        assertEquals("https://fake-email.pro/pricing", e.upgradeUrl());
        assertEquals(new RateLimitInfo(60, 0, 1792659660L), e.rateLimit());
        assertEquals("402 quota_exhausted: monthly mailbox quota exhausted", e.getMessage());
    }

    @Test
    void quotaFieldsAreNullWhenTheBodyLacksThem() {
        QuotaExhaustedError e = assertInstanceOf(QuotaExhaustedError.class, failWith(text(402, "payment required")));
        assertNull(e.limit());
        assertNull(e.used());
        assertNull(e.resetsAt());
        assertNull(e.upgradeUrl());
    }

    @Test
    void rateLimitCarriesRetryAfterAndTheCodeThatTellsTheTwoCasesApart() {
        String[] headers = {"Retry-After", "1", RL[0], RL[1], RL[2], RL[3], RL[4], RL[5]};
        RateLimitError e = assertInstanceOf(
                RateLimitError.class, failWith(apiError(429, "too_many_waits", "too many concurrent waits", headers)));
        assertEquals(ErrorCode.TOO_MANY_WAITS, e.code());
        assertEquals(Duration.ofSeconds(1), e.retryAfter());
        assertEquals(new RateLimitInfo(60, 0, 1792659660L), e.rateLimit());
        assertEquals("1", e.headers().firstValue("retry-after").orElseThrow());
    }

    @Test
    void retryAfterIsRoundedUpToWholeSecondsAndNullWithoutTheHeader() {
        RateLimitError e = assertInstanceOf(
                RateLimitError.class,
                failWith(apiError(429, "rate_limit_exceeded", "rate limit exceeded", "Retry-After", "1.2")));
        assertEquals(Duration.ofSeconds(2), e.retryAfter());
        e = assertInstanceOf(
                RateLimitError.class, failWith(apiError(429, "rate_limit_exceeded", "rate limit exceeded")));
        assertNull(e.retryAfter());
    }

    @Test
    void rateLimitIsNullWithoutXRateLimitHeaders() {
        APIError e =
                assertInstanceOf(APIError.class, failWith(apiError(401, "unauthorized", "missing or invalid API key")));
        assertNull(e.rateLimit());
    }

    @Test
    void rateLimitIsNullWhenAHeaderIsUnreadable() {
        APIError e = assertInstanceOf(
                APIError.class,
                failWith(apiError(
                        400,
                        "bad_request",
                        "x",
                        "X-RateLimit-Limit",
                        "60",
                        "X-RateLimit-Remaining",
                        "n/a",
                        "X-RateLimit-Reset",
                        "1")));
        assertNull(e.rateLimit());
    }

    @Test
    void aNonJsonProxyPageStillMapsByStatus() {
        Harness h = Harness.with(
                b -> b.maxRetries(0), text(502, "<html>502 Bad Gateway</html>", "Content-Type", "text/html"));
        InternalServerError e =
                assertThrows(InternalServerError.class, () -> h.client.domains().list());
        assertEquals(502, e.status());
        assertNull(e.code());
        assertEquals("<html>502 Bad Gateway</html>", e.body());
        assertEquals("502: HTTP 502", e.getMessage());
    }

    @Test
    void aJsonBodyWithoutTheEnvelopeUsesItsMessageIfAny() {
        APIError e = assertInstanceOf(APIError.class, failWith(json(418, obj("message", "short and stout"))));
        assertNull(e.code());
        assertEquals("418: short and stout", e.getMessage());
        e = assertInstanceOf(APIError.class, failWith(json(418, Fixtures.arr(1, 2))));
        assertEquals("418: HTTP 418", e.getMessage());
    }

    @Test
    void anEmptyErrorBodyMapsByStatus() {
        Harness h = Harness.of(empty(404));
        NotFoundError e =
                assertThrows(NotFoundError.class, () -> h.client.domains().list());
        assertNull(e.body());
        assertEquals("404: HTTP 404", e.getMessage());
    }

    @Test
    void aNetworkFailureIsAnApiConnectionErrorWithTheCause() {
        IOException cause = new ConnectException("Connection refused");
        Harness h = Harness.with(b -> b.maxRetries(0), Fixtures.fail(cause));
        APIConnectionError e =
                assertThrows(APIConnectionError.class, () -> h.client.domains().list());
        assertFalse(e instanceof APITimeoutError);
        assertSame(cause, e.getCause());
        assertEquals("connection error: Connection refused", e.getMessage());
    }

    @Test
    void errorsCanBeBuiltByUsersForTheirOwnTests() {
        var headers = FakeHttpClient.headers("Retry-After", "3");
        RateLimitError e =
                new RateLimitError(429, headers, obj("error", "rate_limit_exceeded", "message", "slow down"));
        assertEquals(Duration.ofSeconds(3), e.retryAfter());
        assertEquals("429 rate_limit_exceeded: slow down", e.getMessage());
        assertEquals(
                "no matching message in a@b.c within 1500 ms",
                new WaitTimeoutError("a@b.c", Duration.ofMillis(1500)).getMessage());
    }
}
