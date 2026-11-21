package pro.fakeemail.models;

import java.util.Objects;

/**
 * A domain you can create a mailbox on.
 *
 * @param name domain name in lowercase
 * @param type {@link DomainType#PUBLIC} or {@link DomainType#PREMIUM}
 */
public record Domain(String name, DomainType type) {
    public Domain {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
