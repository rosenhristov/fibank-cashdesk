package bg.fibank.cashdesk.exception;

/**
 * Thrown when a cash operation request is structurally valid but violates a
 * business rule — for example, when the stated {@code amount} does not equal
 * the sum of the provided denomination breakdown.
 *
 * <p>Maps to HTTP {@code 422 Unprocessable Entity}.</p>
 */
public class InvalidOperationException extends CashDeskException {

    public InvalidOperationException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatus() {
        return 422;
    }
}
