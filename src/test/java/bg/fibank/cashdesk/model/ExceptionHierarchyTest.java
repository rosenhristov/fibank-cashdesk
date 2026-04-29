package bg.fibank.cashdesk.model;

import bg.fibank.cashdesk.exception.*;
import bg.fibank.cashdesk.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every domain exception carries the correct HTTP status code
 * and produces a meaningful message. This catches accidental status changes
 * during refactoring before they reach the GlobalExceptionHandler.
 */
class ExceptionHierarchyTest {

    @Test
    @DisplayName("CashierNotFoundException → 404 with cashier name in message")
    void cashierNotFound_status404() {
        CashierNotFoundException ex = new CashierNotFoundException("UNKNOWN");

        assertThat(ex.getHttpStatus()).isEqualTo(404);
        assertThat(ex.getMessage()).contains("UNKNOWN");
    }

    @Test
    @DisplayName("DenominationNotFoundException → 422 with cashier, currency, face value in message")
    void denominationNotFound_status422() {
        DenominationNotFoundException ex =
                new DenominationNotFoundException("MARTINA", Currency.BGN, 200);

        assertThat(ex.getHttpStatus()).isEqualTo(422);
        assertThat(ex.getMessage()).contains("MARTINA");
        assertThat(ex.getMessage()).contains("200");
        assertThat(ex.getMessage()).contains("BGN");
    }

    @Test
    @DisplayName("InsufficientFundsException → 422 with requested and available counts in message")
    void insufficientFunds_status422() {
        InsufficientFundsException ex =
                new InsufficientFundsException("PETER", Currency.EUR, 50, 15, 10);

        assertThat(ex.getHttpStatus()).isEqualTo(422);
        assertThat(ex.getMessage()).contains("PETER");
        assertThat(ex.getMessage()).contains("15");  // requested
        assertThat(ex.getMessage()).contains("10");  // available
        assertThat(ex.getMessage()).contains("EUR");
    }

    @Test
    @DisplayName("InvalidOperationException → 422 with provided message")
    void invalidOperation_status422() {
        InvalidOperationException ex =
                new InvalidOperationException("Amount 500 does not match denomination sum 600.");

        assertThat(ex.getHttpStatus()).isEqualTo(422);
        assertThat(ex.getMessage()).contains("500");
        assertThat(ex.getMessage()).contains("600");
    }

    @Test
    @DisplayName("All domain exceptions extend CashDeskException")
    void allExceptions_extendCashDeskException() {
        assertThat(new CashierNotFoundException("X"))
                .isInstanceOf(CashDeskException.class);
        assertThat(new DenominationNotFoundException("X", Currency.BGN, 10))
                .isInstanceOf(CashDeskException.class);
        assertThat(new InsufficientFundsException("X", Currency.EUR, 50, 1, 0))
                .isInstanceOf(CashDeskException.class);
        assertThat(new InvalidOperationException("X"))
                .isInstanceOf(CashDeskException.class);
    }
}
