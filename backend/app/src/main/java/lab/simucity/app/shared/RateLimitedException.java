package lab.simucity.app.shared;

/** 429 con cabecera Retry-After: el cliente sabe cuánto esperar. */
public class RateLimitedException extends TooManyRequestsException {

    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
