package pro.fakeemail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** {@code Retry-After}: delta-seconds or an HTTP date. */
final class RetryAfter {
    private static final Pattern SECONDS = Pattern.compile("^\\d+(\\.\\d+)?$");
    // Far beyond any cap applied to it, and small enough to never overflow when added to a clock.
    private static final long MAX_MILLIS = 1_000_000_000_000L;

    private RetryAfter() {}

    /** Milliseconds from {@code nowMillis}; {@code null} if absent or unreadable. */
    static Long parseMillis(String value, long nowMillis) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String v = value.trim();
        if (SECONDS.matcher(v).matches()) {
            BigDecimal ms = new BigDecimal(v).multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP);
            return ms.compareTo(BigDecimal.valueOf(MAX_MILLIS)) > 0 ? MAX_MILLIS : ms.longValueExact();
        }
        try {
            long at = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli();
            return Math.max(0, at - nowMillis);
        } catch (DateTimeParseException | ArithmeticException e) {
            return null;
        }
    }
}
