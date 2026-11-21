package pro.fakeemail;

/**
 * Base class of every error this SDK throws. All of them are unchecked.
 *
 * <p>An interrupted call is the exception: it throws {@link java.util.concurrent.CancellationException} with
 * the {@link InterruptedException} as its cause, and the thread's interrupt flag set again.
 */
public class FakeEmailError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FakeEmailError(String message) {
        super(message);
    }

    public FakeEmailError(String message, Throwable cause) {
        super(message, cause);
    }
}
