package pro.fakeemail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pro.fakeemail.Fixtures.arr;
import static pro.fakeemail.Fixtures.detailedMessage;
import static pro.fakeemail.Fixtures.mailbox;
import static pro.fakeemail.Fixtures.obj;
import static pro.fakeemail.Fixtures.simpleMessage;
import static pro.fakeemail.Fixtures.with;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pro.fakeemail.models.BodyState;
import pro.fakeemail.models.DetailedMessage;
import pro.fakeemail.models.Domain;
import pro.fakeemail.models.DomainType;
import pro.fakeemail.models.DownloadedAttachment;
import pro.fakeemail.models.Mailbox;
import pro.fakeemail.models.Page;
import pro.fakeemail.models.SimpleMessage;

class ModelsTest {
    /** Through the JSON text, as the client does. */
    private static Object roundTrip(Object json) {
        return Json.parse(Json.write(json));
    }

    static Stream<Arguments> times() {
        return Stream.of(
                Arguments.of("2026-10-22T18:01:02.123Z", "2026-10-22T18:01:02.123Z"),
                Arguments.of("2026-10-22T18:01:02Z", "2026-10-22T18:01:02Z"),
                Arguments.of("2026-10-22T18:01:02.1234567Z", "2026-10-22T18:01:02.1234567Z"),
                Arguments.of("2026-10-22T21:01:02.5+03:00", "2026-10-22T18:01:02.5Z"),
                Arguments.of("2026-10-22t18:01:02z", "2026-10-22T18:01:02Z"));
    }

    @ParameterizedTest
    @MethodSource("times")
    void parsesRfc3339Times(String value, String expected) {
        assertEquals(Instant.parse(expected), Times.parse(value));
    }

    @Test
    void formatsTimesLikeToIsoString() {
        assertEquals("2026-10-22T09:00:00.999Z", Times.format(Instant.parse("2026-10-22T09:00:00.999999Z")));
        assertEquals("2026-01-02T03:04:05.000Z", Times.format(Instant.parse("2026-01-02T03:04:05Z")));
    }

    @Test
    void unknownFieldsAreIgnored() {
        Mailbox mb = Decode.mailbox(roundTrip(with(mailbox(), "unreadCount", 4, "tags", arr("new"))));
        assertEquals(Fixtures.ADDR, mb.address());
        SimpleMessage m = Decode.simpleMessage(roundTrip(simpleMessage("isRead", true, "priority", obj("x", 1))));
        assertEquals("048213", m.code());
        DetailedMessage full = Decode.detailedMessage(
                roundTrip(detailedMessage("codes", arr("048213"), "headers", obj("X-Foo", "bar"))));
        assertEquals(List.of("048213"), full.codes());
    }

    @Test
    void anUnknownEnumValueDoesNotBreakParsing() {
        SimpleMessage m = Decode.simpleMessage(roundTrip(simpleMessage("bodyState", "QUARANTINED")));
        assertEquals("QUARANTINED", m.bodyState().value());
        assertEquals("QUARANTINED", m.bodyState().toString());
        assertFalse(m.bodyState().isKnown());
        assertNotEquals(BodyState.STORED, m.bodyState());
        assertEquals(BodyState.of("QUARANTINED"), m.bodyState());

        Domain d = Decode.domain(roundTrip(obj("name", "x.example", "type", "PRIVATE")));
        assertEquals("PRIVATE", d.type().value());
        assertFalse(d.type().isKnown());
    }

    @Test
    void knownEnumValuesAreTheConstants() {
        assertSame(
                BodyState.STORED,
                Decode.simpleMessage(roundTrip(simpleMessage())).bodyState());
        assertSame(DomainType.PREMIUM, DomainType.of("PREMIUM"));
        assertTrue(BodyState.OVERFLOW.isKnown());
        assertEquals(List.of(BodyState.STORED, BodyState.UNPARSED, BodyState.OVERFLOW), BodyState.values());
    }

    @Test
    void anUnparsedMessageHasNulls() {
        SimpleMessage m = Decode.simpleMessage(roundTrip(simpleMessage(
                "from", null, "subject", null, "code", null, "confirmLink", null, "bodyState", "UNPARSED")));
        assertNull(m.from());
        assertNull(m.subject());
        assertNull(m.code());
        assertNull(m.confirmLink());
        assertSame(BodyState.UNPARSED, m.bodyState());
    }

    @Test
    void aMissingRequiredFieldIsAFakeEmailError() {
        var data = with(mailbox());
        data.remove("expiresAt");
        FakeEmailError e = assertThrows(FakeEmailError.class, () -> Decode.mailbox(roundTrip(data)));
        assertEquals("unexpected API response: Mailbox.expiresAt is not a string", e.getMessage());
        e = assertThrows(
                FakeEmailError.class, () -> Decode.mailbox(roundTrip(with(mailbox(), "activeSince", "yesterday"))));
        assertEquals("unexpected API response: Mailbox.activeSince is not an RFC 3339 time", e.getMessage());
        e = assertThrows(FakeEmailError.class, () -> Decode.mailbox(roundTrip(with(mailbox(), "messagesCount", 1.5))));
        assertEquals("unexpected API response: Mailbox.messagesCount is not an integer", e.getMessage());
        e = assertThrows(FakeEmailError.class, () -> Decode.mailbox("nope"));
        assertEquals("unexpected API response: Mailbox is not a JSON object", e.getMessage());
    }

    @Test
    void listsAreImmutableCopies() {
        List<String> codes = new ArrayList<>(List.of("1"));
        DetailedMessage m = new DetailedMessage(
                "id", Instant.EPOCH, null, null, BodyState.STORED, null, null, List.of(), codes, List.of());
        codes.add("2");
        assertEquals(List.of("1"), m.codes());
        assertThrows(UnsupportedOperationException.class, () -> m.codes().add("3"));
        Page<String> page = new Page<>(new ArrayList<>(List.of("a")), 0, 1, 2, 2);
        assertThrows(UnsupportedOperationException.class, () -> page.content().clear());
        assertTrue(page.hasNextPage());
        List<String> items = new ArrayList<>();
        page.forEach(items::add);
        assertEquals(List.of("a"), items);
    }

    @Test
    void downloadedAttachmentComparesBytesAndCopiesThem() {
        byte[] data = {1, 2};
        DownloadedAttachment a = new DownloadedAttachment(data, "text/plain", "a.txt");
        data[0] = 9;
        assertEquals(new DownloadedAttachment(new byte[] {1, 2}, "text/plain", "a.txt"), a);
        assertEquals(new DownloadedAttachment(new byte[] {1, 2}, "text/plain", "a.txt").hashCode(), a.hashCode());
        a.data()[1] = 9;
        assertEquals(2, a.data()[1]);
        assertEquals("DownloadedAttachment[data=2 bytes, contentType=text/plain, fileName=a.txt]", a.toString());
    }
}
