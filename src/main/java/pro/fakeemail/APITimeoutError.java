package pro.fakeemail;

/** An HTTP attempt took longer than the timeout ({@link FakeEmail.Builder#timeout}). */
public class APITimeoutError extends APIConnectionError {
    private static final long serialVersionUID = 1L;

    public APITimeoutError(String message, Throwable cause) {
        super(message, cause);
    }
}
