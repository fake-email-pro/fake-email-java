package pro.fakeemail.models;

import java.util.Objects;

/**
 * The sender, from the {@code From} header ({@code From} in the OpenAPI spec).
 *
 * @param name display name; nullable
 * @param address email address
 */
public record Sender(String name, String address) {
    public Sender {
        Objects.requireNonNull(address, "address");
    }
}
