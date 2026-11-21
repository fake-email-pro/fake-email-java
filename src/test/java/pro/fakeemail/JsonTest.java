package pro.fakeemail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pro.fakeemail.Fixtures.arr;
import static pro.fakeemail.Fixtures.obj;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {
    @Test
    void parsesScalars() {
        assertEquals("hi", Json.parse("\"hi\""));
        assertEquals(Boolean.TRUE, Json.parse("true"));
        assertEquals(Boolean.FALSE, Json.parse(" false "));
        assertNull(Json.parse("null"));
        assertEquals(0L, Json.parse("0"));
        assertEquals(-12L, Json.parse("-12"));
        assertEquals(1.5, Json.parse("1.5"));
        assertEquals(-0.25, Json.parse("-2.5e-1"));
        assertEquals(1000.0, Json.parse("1E3"));
        assertEquals(Long.MAX_VALUE, Json.parse("9223372036854775807"));
        assertEquals(Long.MIN_VALUE, Json.parse("-9223372036854775808"));
        assertEquals(new BigInteger("9223372036854775808"), Json.parse("9223372036854775808"));
        assertEquals(1792659660L, Json.parse("1792659660"));
    }

    @Test
    void parsesObjectsAndArraysInOrder() {
        Object value = Json.parse(" { \"b\" : [1, \"x\", null, {\"c\": true}], \"a\": {} , \"e\": [] } ");
        assertEquals(obj("b", arr(1L, "x", null, obj("c", true)), "a", Map.of(), "e", List.of()), value);
        assertEquals(List.of("b", "a", "e"), List.copyOf(((Map<?, ?>) value).keySet()));
    }

    @Test
    void theLastDuplicateKeyWins() {
        assertEquals(Map.of("a", 2L), Json.parse("{\"a\":1,\"a\":2}"));
    }

    @Test
    void parsedContainersAreUnmodifiable() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"a\":[1]}");
        assertThrows(UnsupportedOperationException.class, () -> map.clear());
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) map.get("a")).clear());
    }

    @Test
    void decodesEscapes() {
        assertEquals("\"\\/\b\f\n\r\t", Json.parse("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\""));
        assertEquals("é€", Json.parse("\"\\u00e9\\u20AC\""));
        assertEquals("😀", Json.parse("\"\\ud83d\\ude00\""));
        assertEquals("счёт 😀", Json.parse("\"счёт 😀\""));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                "{",
                "}",
                "[",
                "[1,]",
                "[1 2]",
                "{\"a\"}",
                "{\"a\":}",
                "{a:1}",
                "{\"a\":1,}",
                "01",
                "-",
                "1.",
                ".5",
                "1e",
                "+1",
                "tru",
                "nul",
                "\"abc",
                "\"\\x\"",
                "\"\\u12\"",
                "\"\\u12G4\"",
                "\"a\nb\"",
                "1 2",
                "NaN",
                "Infinity",
                "'a'",
                "[1]]"
            })
    void rejectsInvalidJson(String text) {
        assertThrows(Json.ParseException.class, () -> Json.parse(text));
    }

    @Test
    void limitsNesting() {
        String deep = "[".repeat(Json.MAX_DEPTH + 1) + "]".repeat(Json.MAX_DEPTH + 1);
        Json.ParseException e = assertThrows(Json.ParseException.class, () -> Json.parse(deep));
        assertTrue(e.getMessage().contains("nesting too deep"));
        String ok = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH);
        assertTrue(Json.parse(ok) instanceof List);
    }

    @Test
    void writesValues() {
        assertEquals("{}", Json.write(Map.of()));
        assertEquals(
                "{\"localPart\":\"ci-runner-7\",\"domain\":\"example.org\"}",
                Json.write(obj("localPart", "ci-runner-7", "domain", "example.org")));
        assertEquals("[1,2.5,true,null,\"x\",[],{}]", Json.write(arr(1, 2.5, true, null, "x", List.of(), Map.of())));
        assertEquals("12345678901234567890", Json.write(new BigInteger("12345678901234567890")));
        assertEquals("1.10", Json.write(new BigDecimal("1.10")));
    }

    @Test
    void escapesStrings() {
        assertEquals("\"a\\\"b\\\\c\\n\\r\\t\\b\\f\\u0001\\u2028é😀\"", Json.write("a\"b\\c\n\r\t\b\f\u0001\u2028é😀"));
    }

    @Test
    void roundTrips() {
        Object value =
                obj("s", "line\nbreak \"q\" é", "n", -5L, "d", 0.125, "b", false, "z", null, "l", arr(1L, arr()));
        assertEquals(value, Json.parse(Json.write(value)));
    }

    @Test
    void refusesWhatJsonCannotHold() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of(1, "a")));
    }
}
