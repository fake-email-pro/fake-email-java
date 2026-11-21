package pro.fakeemail.models;

import java.util.List;
import java.util.Objects;

/**
 * Type of a {@link Domain}. {@code PREMIUM} is visible only to plans with premium domains.
 *
 * <p>Not a Java {@code enum}: a newer server may send a value this SDK does not know, and parsing must not fail
 * on it. Known values are the constants below (compare with {@code equals}, or with {@code ==} for values from
 * {@link #of}); an unknown one keeps its raw {@link #value()}.
 */
public final class DomainType {
    public static final DomainType PUBLIC = new DomainType("PUBLIC");
    public static final DomainType PREMIUM = new DomainType("PREMIUM");

    private static final List<DomainType> KNOWN = List.of(PUBLIC, PREMIUM);

    private final String value;

    private DomainType(String value) {
        this.value = value;
    }

    /** The constant for a known value, or a new instance holding an unknown one. */
    public static DomainType of(String value) {
        Objects.requireNonNull(value, "value");
        for (DomainType known : KNOWN) {
            if (known.value.equals(value)) {
                return known;
            }
        }
        return new DomainType(value);
    }

    /** The known values, in the order of the OpenAPI spec. */
    public static List<DomainType> values() {
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
        return o instanceof DomainType && value.equals(((DomainType) o).value);
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
