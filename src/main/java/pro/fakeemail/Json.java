package pro.fakeemail;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small strict JSON (RFC 8259) reader and writer, so the SDK needs no JSON library on the classpath.
 *
 * <p>Reading gives {@code Map<String, Object>} (unmodifiable, insertion order, the last duplicate key wins),
 * {@code List<Object>} (unmodifiable), {@code String}, {@code Boolean}, {@code null}, and for numbers a {@code
 * Long} (an integer that fits), a {@code BigInteger} (a bigger integer) or a {@code Double}.
 */
final class Json {
    /** Deeper nesting than this is rejected rather than risking a stack overflow on hostile input. */
    static final int MAX_DEPTH = 512;

    private Json() {}

    /** Thrown for text that is not valid JSON. */
    static final class ParseException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        ParseException(String message, int position) {
            super(message + " at position " + position);
        }
    }

    static Object parse(String text) {
        Reader reader = new Reader(text);
        reader.skipWhitespace();
        Object value = reader.readValue(0);
        reader.skipWhitespace();
        if (reader.pos != text.length()) {
            throw reader.error("unexpected trailing content");
        }
        return value;
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value, 0);
        return out.toString();
    }

    private static final class Reader {
        private final String s;
        private int pos;

        Reader(String s) {
            this.s = s;
        }

        ParseException error(String message) {
            return new ParseException(message, pos);
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue(int depth) {
            if (pos >= s.length()) {
                throw error("unexpected end of input");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return readObject(depth + 1);
                case '[':
                    return readArray(depth + 1);
                case '"':
                    return readString();
                case 't':
                    expectLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    expectLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    expectLiteral("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return readNumber();
                    }
                    throw error("unexpected character '" + c + "'");
            }
        }

        private void expectLiteral(String literal) {
            if (!s.startsWith(literal, pos)) {
                throw error("invalid literal");
            }
            pos += literal.length();
        }

        private Map<String, Object> readObject(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("nesting too deep");
            }
            pos++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return Collections.unmodifiableMap(map);
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("expected a string key");
                }
                String key = readString();
                skipWhitespace();
                if (peek() != ':') {
                    throw error("expected ':'");
                }
                pos++;
                skipWhitespace();
                map.put(key, readValue(depth));
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == '}') {
                    return Collections.unmodifiableMap(map);
                }
                if (c != ',') {
                    pos--;
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("nesting too deep");
            }
            pos++; // [
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return Collections.unmodifiableList(list);
            }
            while (true) {
                skipWhitespace();
                list.add(readValue(depth));
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == ']') {
                    return Collections.unmodifiableList(list);
                }
                if (c != ',') {
                    pos--;
                    throw error("expected ',' or ']'");
                }
            }
        }

        /** The current character; the end of input is an error wherever peek is used. */
        private char peek() {
            if (pos >= s.length()) {
                throw error("unexpected end of input");
            }
            return s.charAt(pos);
        }

        private String readString() {
            pos++; // opening quote
            StringBuilder sb = null;
            int start = pos;
            while (true) {
                if (pos >= s.length()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(pos);
                if (c == '"') {
                    String result = sb == null
                            ? s.substring(start, pos)
                            : sb.append(s, start, pos).toString();
                    pos++;
                    return result;
                }
                if (c < 0x20) {
                    throw error("control character in string");
                }
                if (c != '\\') {
                    pos++;
                    continue;
                }
                if (sb == null) {
                    sb = new StringBuilder();
                }
                sb.append(s, start, pos);
                pos++;
                if (pos >= s.length()) {
                    throw error("unterminated escape");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        sb.append(readHex4());
                        break;
                    default:
                        pos--;
                        throw error("invalid escape '\\" + e + "'");
                }
                start = pos;
            }
        }

        private char readHex4() {
            if (pos + 4 > s.length()) {
                throw error("truncated \\u escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(s.charAt(pos + i), 16);
                if (digit < 0) {
                    throw error("invalid \\u escape");
                }
                value = value * 16 + digit;
            }
            pos += 4;
            // Surrogates are kept as they are: a pair becomes the code point, a lone one stays a lone char.
            return (char) value;
        }

        private Object readNumber() {
            int start = pos;
            if (s.charAt(pos) == '-') {
                pos++;
            }
            if (pos >= s.length()) {
                throw error("invalid number");
            }
            if (s.charAt(pos) == '0') {
                pos++;
            } else if (isDigit(pos)) {
                while (isDigit(pos)) {
                    pos++;
                }
            } else {
                throw error("invalid number");
            }
            boolean integer = true;
            if (pos < s.length() && s.charAt(pos) == '.') {
                integer = false;
                pos++;
                if (!isDigit(pos)) {
                    throw error("invalid number");
                }
                while (isDigit(pos)) {
                    pos++;
                }
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                integer = false;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    pos++;
                }
                if (!isDigit(pos)) {
                    throw error("invalid number");
                }
                while (isDigit(pos)) {
                    pos++;
                }
            }
            String text = s.substring(start, pos);
            if (!integer) {
                return Double.parseDouble(text);
            }
            if (text.length() < 19) {
                return Long.parseLong(text);
            }
            BigInteger big = new BigInteger(text);
            return big.bitLength() < 64 ? (Object) big.longValue() : big;
        }

        private boolean isDigit(int i) {
            return i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9';
        }
    }

    private static void writeValue(StringBuilder out, Object value, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("nesting too deep");
        }
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            writeString(out, (String) value);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON has no NaN or Infinity");
            }
            out.append(value);
        } else if (value instanceof Integer
                || value instanceof Long
                || value instanceof Short
                || value instanceof Byte
                || value instanceof BigInteger) {
            out.append(value);
        } else if (value instanceof BigDecimal) {
            out.append(((BigDecimal) value).toString());
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, (String) entry.getKey());
                out.append(':');
                writeValue(out, entry.getValue(), depth + 1);
            }
            out.append('}');
        } else if (value instanceof Iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, item, depth + 1);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException(
                    "cannot write " + value.getClass().getName() + " as JSON");
        }
    }

    private static void writeString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
