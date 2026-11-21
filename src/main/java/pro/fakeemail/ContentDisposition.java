package pro.fakeemail;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The file name of a {@code Content-Disposition} header. */
final class ContentDisposition {
    // name=token | name="quoted \" string"; the leading disposition type has no "=" and is skipped.
    private static final Pattern PARAM =
            Pattern.compile(";\\s*([^\\s=;]+)\\s*=\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^;]*))");
    private static final Pattern ESCAPE = Pattern.compile("\\\\(.)");
    private static final Pattern EXT_VALUE = Pattern.compile("^([\\w!#$%&+^`{}~-]*)'[\\w-]*'(.*)$");
    private static final Pattern LATIN1_ESCAPE = Pattern.compile("%([0-9a-fA-F]{2})");

    private ContentDisposition() {}

    /** {@code filename*} (RFC 5987/6266) wins over {@code filename}; {@code null} when neither is present. */
    static String fileName(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        Map<String, String> params = params(header);
        String extended = params.get("filename*");
        if (extended != null) {
            Matcher m = EXT_VALUE.matcher(extended);
            if (m.matches()) {
                String decoded = decodeExtValue(m.group(2), m.group(1).toLowerCase(Locale.ROOT));
                if (decoded != null) {
                    return decoded;
                }
            }
        }
        String plain = params.get("filename");
        return plain == null || plain.isEmpty() ? null : plain;
    }

    private static Map<String, String> params(String header) {
        Map<String, String> params = new HashMap<>();
        Matcher m = PARAM.matcher(header);
        while (m.find()) {
            String name = m.group(1).toLowerCase(Locale.ROOT);
            String value = m.group(2) != null
                    ? ESCAPE.matcher(m.group(2)).replaceAll("$1")
                    : m.group(3).trim();
            params.putIfAbsent(name, value);
        }
        return params;
    }

    private static String decodeExtValue(String value, String charset) {
        if (charset.equals("utf-8") || charset.isEmpty()) {
            return decodeUtf8Percent(value);
        }
        if (charset.equals("iso-8859-1")) {
            Matcher m = LATIN1_ESCAPE.matcher(value);
            StringBuilder out = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(
                        out, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
            }
            m.appendTail(out);
            return out.toString();
        }
        return null;
    }

    /** JavaScript's {@code decodeURIComponent}: {@code null} on a malformed escape or invalid UTF-8. */
    private static String decodeUtf8Percent(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < value.length(); ) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= value.length()) {
                    return null;
                }
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    return null;
                }
                bytes.write(hi * 16 + lo);
                i += 3;
            } else {
                int cp = value.codePointAt(i);
                byte[] encoded = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
                i += Character.charCount(cp);
            }
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
