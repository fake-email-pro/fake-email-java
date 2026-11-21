package pro.fakeemail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ContentDispositionTest {
    static Stream<Arguments> cases() {
        return Stream.of(
                // The JS suite's cases.
                Arguments.of(
                        "RFC 5987 UTF-8 wins over the ASCII fallback",
                        "attachment; filename=\"invoice_.pdf\"; filename*=UTF-8''%D1%81%D1%87%D1%91%D1%82.pdf",
                        "счёт.pdf"),
                Arguments.of(
                        "filename* first", "attachment; filename*=UTF-8''a%20b.txt; filename=\"a b.txt\"", "a b.txt"),
                Arguments.of(
                        "charset is case-insensitive, language tag allowed",
                        "attachment; filename*=utf-8'en'na%C3%AFve.txt",
                        "naïve.txt"),
                Arguments.of("ISO-8859-1", "attachment; filename*=ISO-8859-1''caf%E9.txt", "café.txt"),
                Arguments.of("quoted plain", "attachment; filename=\"invoice.pdf\"", "invoice.pdf"),
                Arguments.of(
                        "quoted with escapes and ;", "attachment; filename=\"a \\\"b\\\"; c.pdf\"", "a \"b\"; c.pdf"),
                Arguments.of("token", "attachment; filename=report.csv", "report.csv"),
                Arguments.of("case-insensitive names", "Attachment; FileName=\"X.PDF\"", "X.PDF"),
                Arguments.of(
                        "no name: the server's attachment id",
                        "attachment; filename=\"9a7e2c44-0d0b-4f7a-a1c1-2b9d5f3e6a10\"",
                        "9a7e2c44-0d0b-4f7a-a1c1-2b9d5f3e6a10"),
                Arguments.of(
                        "malformed filename* falls back",
                        "attachment; filename=\"ok.txt\"; filename*=UTF-8''%E2%82",
                        "ok.txt"),
                Arguments.of(
                        "unknown charset falls back",
                        "attachment; filename=\"ok.txt\"; filename*=KOI8-R''%C1",
                        "ok.txt"),
                // The Python suite's extra cases.
                Arguments.of(
                        "escaped ; and quote in filename*",
                        "attachment; filename*=utf-8'en'na%20me%3B%22.txt",
                        "na me;\".txt"),
                Arguments.of(
                        "upper-case FILENAME*",
                        "attachment; FILENAME*=UTF-8''r%C3%A9sum%C3%A9.docx; filename=\"resume.docx\"",
                        "résumé.docx"),
                Arguments.of(
                        "bogus charset falls back",
                        "attachment; filename=\"fallback.bin\"; filename*=bogus-charset''x",
                        "fallback.bin"),
                // Edge cases of decodeURIComponent.
                Arguments.of(
                        "truncated escape falls back",
                        "attachment; filename=\"ok.txt\"; filename*=UTF-8''a%2",
                        "ok.txt"),
                Arguments.of(
                        "non-hex escape falls back",
                        "attachment; filename=\"ok.txt\"; filename*=UTF-8''a%zz",
                        "ok.txt"),
                Arguments.of("plus is not a space", "attachment; filename*=UTF-8''a+b.txt", "a+b.txt"),
                Arguments.of("no charset means UTF-8", "attachment; filename*=''%C3%A9.txt", "é.txt"),
                Arguments.of(
                        "filename* without quotes structure falls back",
                        "attachment; filename=\"ok.txt\"; filename*=plain",
                        "ok.txt"),
                Arguments.of("first occurrence wins", "attachment; filename=\"a.txt\"; filename=\"b.txt\"", "a.txt"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void parsesTheFileName(String name, String header, String expected) {
        assertEquals(expected, ContentDisposition.fileName(header));
    }

    @Test
    void returnsNullWithoutAHeaderOrAName() {
        assertNull(ContentDisposition.fileName(null));
        assertNull(ContentDisposition.fileName(""));
        assertNull(ContentDisposition.fileName("attachment"));
        assertNull(ContentDisposition.fileName("attachment; filename=\"\""));
    }
}
