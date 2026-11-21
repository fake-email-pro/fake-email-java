package pro.fakeemail;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Parameters of one long poll, {@link MessagesResource#wait(String, WaitParams)}.
 *
 * <pre>{@code
 * WaitParams.builder().timeout(Duration.ofSeconds(30)).after(previous.id()).subject("code").build();
 * }</pre>
 */
public final class WaitParams {
    private final Integer timeoutSeconds;
    private final String after;
    private final String from;
    private final String subject;

    private WaitParams(Integer timeoutSeconds, String after, String from, String subject) {
        this.timeoutSeconds = timeoutSeconds;
        this.after = after;
        this.from = from;
        this.subject = subject;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Server-side wait in whole seconds, or {@code null} for the server's default (30). */
    public Integer timeoutSeconds() {
        return timeoutSeconds;
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

    /** Builder of {@link WaitParams}. */
    public static final class Builder {
        private Integer timeoutSeconds;
        private String after;
        private String from;
        private String subject;

        private Builder() {}

        /** How long the server holds the request, 1..60 s (default 30); sent in whole seconds, rounded up. */
        public Builder timeout(Duration timeout) {
            this.timeoutSeconds = seconds(timeout);
            return this;
        }

        /**
         * Only messages strictly after this message id: use the id of a message you got, to wait for the next
         * one.
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

        /** Case-insensitive substring of the sender address. */
        public Builder from(String from) {
            this.from = from;
            return this;
        }

        /** Case-insensitive substring of the subject. */
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public WaitParams build() {
            return new WaitParams(timeoutSeconds, after, from, subject);
        }
    }

    static int seconds(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        long ms = timeout.toMillis();
        long seconds = ms / 1000 + (ms % 1000 > 0 ? 1 : 0);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, seconds));
    }
}
