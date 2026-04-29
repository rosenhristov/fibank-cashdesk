package bg.fibank.cashdesk.exception;


/**
 * Root of the Cash Desk exception hierarchy.
 *
 * <p>All domain-specific exceptions extend this class so controllers and tests
 * can catch the whole family with a single {@code catch (CashDeskException e)}
 * while still having access to the specific subtype when needed.</p>
 *
 * <p>Every subclass must supply an HTTP status hint via {@link #getHttpStatus()}
 * so the {@code GlobalExceptionHandler} (Phase 7) can map them to correct
 * response codes without a cascade of {@code instanceof} checks.</p>
 */
public abstract class CashDeskException extends RuntimeException {

    protected CashDeskException(String message) {
        super(message);
    }

    /**
     * The HTTP status code that best represents this error.
     * Subclasses must override this.
     */
    public abstract int getHttpStatus();
}
