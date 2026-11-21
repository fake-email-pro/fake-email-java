package pro.fakeemail;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;
import pro.fakeemail.models.SimpleMessage;

/**
 * Options of {@link FakeEmail#waitForMessage}, {@link FakeEmail#waitForCode} and {@link FakeEmail#waitForLink}.
 *
 * <pre>{@code
 * WaitOptions.builder()
 *     .timeout(Duration.ofSeconds(60))
 *     .from("acme.example")
 *     .subject("verification")
 *     .match(m -> m.attachmentsCount() > 0)
 *     .build();
 * }</pre>
 */
public final class WaitOptions {
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final WaitOptions DEFAULTS = builder().build();

    private final Duration timeout;
    private final String after;
    private final String from;
    private final String subject;
    private final Predicate<SimpleMessage> match;

    private WaitOptions(Builder b) {
        this.timeout = b.timeout;
        this.after = b.after;
        this.from = b.from;
        this.subject = b.subject;
        this.match = b.match;
    }

    /** Default options: 60 s, no filters. */
    public static WaitOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Client-side deadline for the whole wait. */
    public Duration timeout() {
        return timeout;
    }

    /** A message id or an RFC 3339 time, or {@code null}. */
    public String after() {
        return after;
    }

    /** Case-insensitive substring of the sender address, or {@code null}. */
    public String from() {
        return from;
    }

    /** Case-insensitive substring of the subject, or {@code null}. */
    public String subject() {
        return subject;
    }

    /** Client-side filter, or {@code null}. */
    public Predicate<SimpleMessage> match() {
        return match;
    }

    /** Builder of {@link WaitOptions}. */
    public static final class Builder {
        private Duration timeout = DEFAULT_TIMEOUT;
        private String after;
        private String from;
        private String subject;
        private Predicate<SimpleMessage> match;

        private Builder() {}

        /**
         * Client-side deadline for the whole wait (default 60 s); then {@link WaitTimeoutError}. Zero or negative
         * means already expired: the wait throws {@link WaitTimeoutError} at once, without a request.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /**
         * Only messages strictly after this message id, e.g. the previous message's id to wait for the next one.
         * An id that is not in the mailbox is a {@link NotFoundError}. (When a message the wait itself skipped is
         * deleted meanwhile, the wait goes on after that message's time instead.)
         */
        public Builder after(String messageIdOrTime) {
            this.after = messageIdOrTime;
            return this;
        }

        /**
         * Only messages that arrived after this time, by the server's clock. Prefer a message id; do not pass a
         * message's {@code createdAt}.
         */
        public Builder after(Instant time) {
            this.after = time == null ? null : Times.format(time);
            return this;
        }

        /** Case-insensitive substring of the sender address (server-side filter). */
        public Builder from(String from) {
            this.from = from;
            return this;
        }

        /** Case-insensitive substring of the subject (server-side filter). */
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        /** Client-side filter; a message that fails it is skipped and the wait continues after it. */
        public Builder match(Predicate<SimpleMessage> match) {
            this.match = match;
            return this;
        }

        public WaitOptions build() {
            return new WaitOptions(this);
        }
    }
}
