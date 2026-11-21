package pro.fakeemail.models;

import java.util.List;
import java.util.Objects;

/**
 * How much of a message is stored: {@code STORED} - parsed, the body and the EML are kept; {@code UNPARSED} -
 * could not be parsed, only the EML is kept (no body, attachments, {@code from} or {@code subject}); {@code
 * OVERFLOW} - larger than the account's whole storage, only the headers are kept, no EML.
 *
 * <p>Not a Java {@code enum}: a newer server may send a value this SDK does not know, and parsing must not fail
 * on it. Known values are the constants below (compare with {@code equals}, or with {@code ==} for values from
 * {@link #of}); an unknown one keeps its raw {@link #value()}.
 */
public final class BodyState {
    public static final BodyState STORED = new BodyState("STORED");
    public static final BodyState UNPARSED = new BodyState("UNPARSED");
    public static final BodyState OVERFLOW = new BodyState("OVERFLOW");

    private static final List<BodyState> KNOWN = List.of(STORED, UNPARSED, OVERFLOW);

    private final String value;

    private BodyState(String value) {
        this.value = value;
    }

    /** The constant for a known value, or a new instance holding an unknown one. */
    public static BodyState of(String value) {
        Objects.requireNonNull(value, "value");
        for (BodyState known : KNOWN) {
            if (known.value.equals(value)) {
                return known;
            }
        }
        return new BodyState(value);
    }

    /** The known values, in the order of the OpenAPI spec. */
    public static List<BodyState> values() {
        return KNOWN;
    }

    /** The value as sent by the API. */
    public String value() {
        return value;
    }

    /** Whether this SDK version knows the value. */
    public boolean isKnown() {
        return KNOWN.contains(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BodyState && value.equals(((BodyState) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
