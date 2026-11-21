package pro.fakeemail;

import java.time.Duration;

/**
 * {@link FakeEmail#waitForMessage}, {@link FakeEmail#waitForCode} or {@link FakeEmail#waitForLink} reached
 * their deadline without a matching message. When transient failures (429, 5xx gateways, timeouts) were being
 * waited out, the last one is the {@link #getCause() cause}.
 */
public class WaitTimeoutError extends FakeEmailError {
    private static final long serialVersionUID = 1L;

    private final String address;
    private final Duration timeout;

    public WaitTimeoutError(String address, Duration timeout) {
        super("no matching message in " + address + " within " + timeout.toMillis() + " ms");
        this.address = address;
        this.timeout = timeout;
    }

    /** The mailbox that was waited on. */
    public String address() {
        return address;
    }

    /** The client-side deadline of the wait. */
    public Duration timeout() {
        return timeout;
    }
}
