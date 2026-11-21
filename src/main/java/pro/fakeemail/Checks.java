package pro.fakeemail;

import java.time.Duration;
import java.util.Objects;

/** Argument checks shared by the builders. */
final class Checks {
    private Checks() {}

    static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
        return value;
    }

    static int notNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, got " + value);
        }
        return value;
    }
}
