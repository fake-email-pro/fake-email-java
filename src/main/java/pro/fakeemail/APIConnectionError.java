package pro.fakeemail;

/** The request did not get a response (DNS, refused connection, reset). */
public class APIConnectionError extends FakeEmailError {
    private static final long serialVersionUID = 1L;

    public APIConnectionError(String message, Throwable cause) {
        super(message, cause);
    }
}
