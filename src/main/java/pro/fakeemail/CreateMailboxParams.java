package pro.fakeemail;

/**
 * Parameters of {@link MailboxesResource#create(CreateMailboxParams)}. Without any: a random address on a
 * random domain.
 *
 * <pre>{@code
 * mail.mailboxes().create(CreateMailboxParams.builder().domain("example.org").build());
 * mail.mailboxes().create(CreateMailboxParams.builder().localPart("signup-test").domain("example.org").build());
 * }</pre>
 */
public final class CreateMailboxParams {
    private final String localPart;
    private final String domain;

    private CreateMailboxParams(String localPart, String domain) {
        this.localPart = localPart;
        this.domain = domain;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Local part (the server lowercases it), or {@code null}. */
    public String localPart() {
        return localPart;
    }

    /** Domain, or {@code null} for a random one. */
    public String domain() {
        return domain;
    }

    /** Builder of {@link CreateMailboxParams}. */
    public static final class Builder {
        private String localPart;
        private String domain;

        private Builder() {}

        /**
         * Local part of the address, lowercased by the server; requires {@link #domain} and a plan that allows
         * custom local parts.
         */
        public Builder localPart(String localPart) {
            this.localPart = localPart;
            return this;
        }

        /** A domain from {@code domains().list()}; random if not set. */
        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public CreateMailboxParams build() {
            return new CreateMailboxParams(localPart, domain);
        }
    }
}
