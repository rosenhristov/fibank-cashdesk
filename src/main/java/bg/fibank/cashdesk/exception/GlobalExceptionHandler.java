package bg.fibank.cashdesk.exception;

import bg.fibank.cashdesk.dto.ErrorResponse;
import bg.fibank.cashdesk.filter.AuthHeaderFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.UncheckedIOException;
import java.util.List;

import static java.util.Objects.nonNull;

/**
 * Central exception-to-HTTP mapping for the Cash Desk API.
 *
 * <p>Every {@code @ExceptionHandler} method follows the same pattern:</p>
 * <ol>
 *   <li>Log the event at the appropriate level.</li>
 *   <li>Build an {@link ErrorResponse} with the correct status and message.</li>
 *   <li>Return a {@link ResponseEntity} so Spring sets both the body and the
 *       status code consistently.</li>
 * </ol>
 *
 * <h2>Handler order (most-specific to least-specific)</h2>
 * <ol>
 *   <li>{@link MethodArgumentNotValidException} → 400 — bean validation failures</li>
 *   <li>{@link HttpMessageNotReadableException} → 400 — malformed JSON or bad enum value</li>
 *   <li>{@link CashDeskException} subtypes     → status from {@link CashDeskException#getHttpStatus()}</li>
 *   <li>{@link UncheckedIOException}           → 500 — file I/O failures</li>
 *   <li>{@link Exception}                      → 500 — unexpected catch-all</li>
 * </ol>
 *
 * <p>The {@link AuthHeaderFilter} writes its own 401 JSON directly (before the
 * request reaches any controller), so there is no handler here for 401.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@code @Valid} failures on {@code @RequestBody} parameters.
     *
     * <p>Collects every field-level violation into the {@link ErrorResponse#violations()}
     * list so the caller knows exactly which fields to fix.</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {

        List<ErrorResponse.Violation> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.Violation(fe.getField(), fe.getDefaultMessage()))
                .toList();

        log.warn("VALIDATION_ERROR | {} violation(s): {}", violations.size(), violations);

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.ofValidation(
                        "Request validation failed — see 'violations' for details",
                        violations));
    }

    /**
     * Handles malformed JSON bodies and unrecognised enum values.
     *
     * <p>Spring throws this when Jackson cannot deserialise the request body —
     * for example, when {@code operationType} is {@code "TRANSFER"} instead of
     * {@code "DEPOSIT"} or {@code "WITHDRAWAL"}.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause().getMessage();

        log.warn("UNREADABLE_REQUEST | {}", detail);

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(400, "Bad Request",
                        "Malformed request body: " + detail));
    }


    /**
     * Handles all {@link CashDeskException} subtypes.
     *
     * <p>The HTTP status is determined by {@link CashDeskException#getHttpStatus()},
     * which each subtype overrides:</p>
     * <ul>
     *   <li>{@link CashierNotFoundException}      → 404</li>
     *   <li>{@link DenominationNotFoundException} → 422</li>
     *   <li>{@link InsufficientFundsException}    → 422</li>
     *   <li>{@link InvalidOperationException}     → 422</li>
     * </ul>
     */
    @ExceptionHandler(CashDeskException.class)
    public ResponseEntity<ErrorResponse> handleCashDesk(CashDeskException ex) {
        int status = ex.getHttpStatus();
        String reason;
        HttpStatus resolvedStatus = HttpStatus.resolve(status);
        if (nonNull(resolvedStatus)) {
            reason = resolvedStatus.getReasonPhrase();
        } else {
            reason = "Error";
        }

        log.warn("DOMAIN_ERROR | status={} | {}", status, ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(status, reason, ex.getMessage()));
    }


    /**
     * Handles file I/O failures that escaped the repository layer.
     *
     * <p>The internal cause message is logged but <em>not</em> included in the
     * API response — file paths and OS error strings are implementation details
     * that must not leak to callers.</p>
     */
    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ErrorResponse> handleIo(UncheckedIOException ex) {
        log.error("IO_ERROR | {}", ex.getMessage(), ex);

        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "A storage error occurred. Please try again or contact support."));
    }

    /**
     * Catch-all for any exception not matched by a more specific handler.
     *
     * <p>Logs the full stack trace at ERROR level and returns a generic 500
     * without exposing internal details to the caller.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("UNEXPECTED_ERROR | {}", ex.getMessage(), ex);

        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please try again or contact support."));
    }
}

