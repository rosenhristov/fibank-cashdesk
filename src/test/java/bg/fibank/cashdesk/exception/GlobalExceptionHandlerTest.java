package bg.fibank.cashdesk.exception;

import bg.fibank.cashdesk.dto.ErrorResponse;
import bg.fibank.cashdesk.model.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Each handler method is called directly — no MockMvc, no Spring context.
 * This keeps tests fast and keeps the handler's contract explicit and
 * independently verifiable from the controllers that use it.</p>
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ── MethodArgumentNotValidException → 400 ─────────────────────────────────

    @Nested
    @DisplayName("MethodArgumentNotValidException → 400 with violations list")
    class ValidationErrors {

        @Test
        @DisplayName("returns 400 status")
        void returns400() {
            ResponseEntity<ErrorResponse> response = handler.handleValidation(buildValidationEx());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("body contains violations list with one entry per field error")
        void bodyContainsViolations() {
            ResponseEntity<ErrorResponse> response = handler.handleValidation(buildValidationEx());
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().violations()).hasSize(2);
        }

        @Test
        @DisplayName("violation fields map to the correct field names")
        void violationFieldNames() {
            ResponseEntity<ErrorResponse> response = handler.handleValidation(buildValidationEx());
            var fields = response.getBody().violations().stream()
                    .map(ErrorResponse.Violation::field)
                    .toList();
            assertThat(fields).containsExactlyInAnyOrder("cashierName", "amount");
        }

        @Test
        @DisplayName("violation messages match the constraint messages")
        void violationMessages() {
            ResponseEntity<ErrorResponse> response = handler.handleValidation(buildValidationEx());
            var messages = response.getBody().violations().stream()
                    .map(ErrorResponse.Violation::message)
                    .toList();
            assertThat(messages).containsExactlyInAnyOrder(
                    "cashierName must not be blank",
                    "amount must be a positive integer");
        }

        @Test
        @DisplayName("status code in body matches HTTP status")
        void bodyStatusMatchesHttp() {
            ResponseEntity<ErrorResponse> response = handler.handleValidation(buildValidationEx());
            assertThat(response.getBody().status()).isEqualTo(400);
        }

        @Test
        @DisplayName("timestamp is populated")
        void timestampPopulated() {
            ResponseEntity<ErrorResponse> response = handler.handleValidation(buildValidationEx());
            assertThat(response.getBody().timestamp()).isNotNull();
        }

        // ── helper ────────────────────────────────────────────────────────────

        private MethodArgumentNotValidException buildValidationEx() {
            // BindingResult with two field errors matching real DTO constraints
            BeanPropertyBindingResult bindingResult =
                    new BeanPropertyBindingResult(new Object(), "cashOperationRequest");

            bindingResult.addError(new FieldError(
                    "cashOperationRequest", "cashierName",
                    "cashierName must not be blank"));
            bindingResult.addError(new FieldError(
                    "cashOperationRequest", "amount",
                    "amount must be a positive integer"));

            return new MethodArgumentNotValidException(null, bindingResult);
        }
    }

    // ── HttpMessageNotReadableException → 400 ────────────────────────────────

    @Nested
    @DisplayName("HttpMessageNotReadableException → 400")
    class UnreadableBody {

        @Test
        @DisplayName("returns 400 status")
        void returns400() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleUnreadable(buildUnreadableEx("Invalid enum value: TRANSFER"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("body message contains 'Malformed request body'")
        void messageContainsMalformed() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleUnreadable(buildUnreadableEx("Unexpected token"));

            assertThat(response.getBody().message()).contains("Malformed request body");
        }

        @Test
        @DisplayName("violations list is null (not a validation error)")
        void noViolationsList() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleUnreadable(buildUnreadableEx("Bad JSON"));

            assertThat(response.getBody().violations()).isNull();
        }

        private HttpMessageNotReadableException buildUnreadableEx(String message) {
            return new HttpMessageNotReadableException(
                    message,
                    new MockHttpInputMessage("{}".getBytes(StandardCharsets.UTF_8)));
        }
    }

    // ── CashDeskException subtypes ────────────────────────────────────────────

    @Nested
    @DisplayName("CashDeskException subtypes → status from getHttpStatus()")
    class DomainErrors {

        @Test
        @DisplayName("CashierNotFoundException → 404")
        void cashierNotFound_404() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleCashDesk(new CashierNotFoundException("NOBODY"));

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody().message()).contains("NOBODY");
        }

        @Test
        @DisplayName("DenominationNotFoundException → 422")
        void denominationNotFound_422() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleCashDesk(
                            new DenominationNotFoundException("MARTINA", Currency.BGN, 200));

            assertThat(response.getStatusCode().value()).isEqualTo(422);
            assertThat(response.getBody().message()).contains("200");
        }

        @Test
        @DisplayName("InsufficientFundsException → 422")
        void insufficientFunds_422() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleCashDesk(
                            new InsufficientFundsException("PETER", Currency.EUR, 50, 15, 10));

            assertThat(response.getStatusCode().value()).isEqualTo(422);
            assertThat(response.getBody().message()).contains("PETER");
        }

        @Test
        @DisplayName("InvalidOperationException → 422")
        void invalidOperation_422() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleCashDesk(new InvalidOperationException("Sum mismatch."));

            assertThat(response.getStatusCode().value()).isEqualTo(422);
            assertThat(response.getBody().message()).isEqualTo("Sum mismatch.");
        }

        @Test
        @DisplayName("body error phrase matches the HTTP status reason phrase")
        void errorPhraseMatchesStatus() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleCashDesk(new CashierNotFoundException("X"));

            assertThat(response.getBody().error()).isEqualTo("Not Found");
        }

        @Test
        @DisplayName("domain errors have no violations list")
        void noViolationsList() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleCashDesk(new InvalidOperationException("msg"));

            assertThat(response.getBody().violations()).isNull();
        }
    }

    // ── UncheckedIOException → 500 ────────────────────────────────────────────

    @Nested
    @DisplayName("UncheckedIOException → 500")
    class IoErrors {

        @Test
        @DisplayName("returns 500 status")
        void returns500() {
            ResponseEntity<ErrorResponse> response = handler.handleIo(
                    new UncheckedIOException("disk full", new IOException("No space left")));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("body does not expose internal file path details")
        void doesNotLeakInternalDetails() {
            ResponseEntity<ErrorResponse> response = handler.handleIo(
                    new UncheckedIOException("disk full",
                            new IOException("/data/cash_balances.txt: No space left on device")));

            // Internal file path must not reach the caller
            assertThat(response.getBody().message())
                    .doesNotContain("cash_balances.txt")
                    .doesNotContain("/data");
        }
    }

    // ── Generic Exception → 500 ───────────────────────────────────────────────

    @Nested
    @DisplayName("Exception (catch-all) → 500")
    class GenericErrors {

        @Test
        @DisplayName("returns 500 status for any unexpected exception")
        void returns500() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleGeneric(new RuntimeException("Unexpected"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("body does not expose internal exception message")
        void doesNotLeakExceptionMessage() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleGeneric(
                            new RuntimeException("Secret internal implementation detail"));

            assertThat(response.getBody().message())
                    .doesNotContain("Secret internal implementation detail");
        }

        @Test
        @DisplayName("body error field is 'Internal Server Error'")
        void errorFieldIsInternalServerError() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleGeneric(new NullPointerException());

            assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        }
    }

    // ── ErrorResponse.of() factories ─────────────────────────────────────────

    @Nested
    @DisplayName("ErrorResponse factory methods")
    class ErrorResponseFactories {

        @Test
        @DisplayName("ErrorResponse.of() sets all fields and leaves violations null")
        void of_setsAllFields() {
            ErrorResponse er = ErrorResponse.of(404, "Not Found", "Resource missing");

            assertThat(er.status()).isEqualTo(404);
            assertThat(er.error()).isEqualTo("Not Found");
            assertThat(er.message()).isEqualTo("Resource missing");
            assertThat(er.timestamp()).isNotNull();
            assertThat(er.violations()).isNull();
        }

        @Test
        @DisplayName("ErrorResponse.ofValidation() sets status 400 and includes violations")
        void ofValidation_setsViolations() {
            var violations = List.of(
                    new ErrorResponse.Violation("field", "must not be blank"));

            ErrorResponse er = ErrorResponse.ofValidation("Validation failed", violations);

            assertThat(er.status()).isEqualTo(400);
            assertThat(er.violations()).hasSize(1);
            assertThat(er.violations().get(0).field()).isEqualTo("field");
        }
    }
}
