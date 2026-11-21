package pro.fakeemail;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/** RFC 3339 times as the API reads and writes them. */
final class Times {
    private static final DateTimeFormatter PARSER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOffsetId()
            .toFormatter();

    // Like JavaScript's Date.toISOString: UTC, always three fraction digits (truncated), "Z".
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Times() {}

    /** Parses an RFC 3339 time with any offset (or {@code Z}) and any number of fraction digits. */
    static Instant parse(String value) {
        return OffsetDateTime.parse(value, PARSER).toInstant();
    }

    static String format(Instant value) {
        return FORMATTER.format(value);
    }
}
