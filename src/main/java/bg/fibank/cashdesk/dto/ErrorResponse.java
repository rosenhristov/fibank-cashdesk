package bg.fibank.cashdesk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Uniform JSON error body returned by {@code GlobalExceptionHandler} for every
 * non-2xx response produced by the application.
 *
 * <p>The {@link #violations} list is only present when the error was caused by
 * bean-validation failures ({@code 400 Bad Request}); for all other error types
 * it is omitted from serialisation via {@link JsonInclude.Include#NON_NULL}.</p>
 *
 * <p>Example — validation error:</p>
 * <pre>{@code
 * {
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Request validation failed — see 'violations' for details",
 *   "timestamp": "2025-04-29T10:00:00",
 *   "violations": [
 *     { "field": "cashierName", "message": "cashierName must not be blank" },
 *     { "field": "amount",      "message": "amount must be a positive integer" }
 *   ]
 * }
 * }</pre>
 *
 * <p>Example — domain error:</p>
 * <pre>{@code
 * {
 *   "status": 422,
 *   "error": "Unprocessable Entity",
 *   "message": "Cashier not found: 'NOBODY'. Valid cashiers are: MARTINA, PETER, LINDA.",
 *   "timestamp": "2025-04-29T10:00:01"
 * }
 * }</pre>
 *
 * @param status     HTTP status code
 * @param error      standard HTTP reason phrase for {@link #status}
 * @param message    human-readable description of what went wrong
 * @param timestamp  when the error occurred (ISO-8601 local date-time)
 * @param violations field-level constraint violations; {@code null} when not applicable
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp,
        List<Violation> violations
) {

    /**
     * Factory for domain / infrastructure errors (no violation list).
     */
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, LocalDateTime.now(), null);
    }

    /**
     * Factory for validation errors (includes a violation list).
     */
    public static ErrorResponse ofValidation(String message, List<Violation> violations) {
        return new ErrorResponse(400, "Bad Request", message, LocalDateTime.now(), violations);
    }

    /**
     * A single field-level constraint violation within a validation error response.
     *
     * @param field   the name of the field that failed validation
     * @param message the constraint message declared on that field's annotation
     */
    public record Violation(String field, String message) {}
}
