package bg.fibank.cashdesk.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link CashBalanceRequest} record.
 *
 * <p>Verifies that the record correctly holds optional fields and that its
 * accessor names match the query-parameter names expected by the controller
 * ({@code cashier}, {@code dateFrom}, {@code dateTo}).</p>
 */
class CashBalanceRequestTest {

    @Test
    @DisplayName("all-null constructor produces a request with no filters applied")
    void allNull_noFilters() {
        CashBalanceRequest request = new CashBalanceRequest(null, null, null);

        assertThat(request.cashier()).isNull();
        assertThat(request.dateFrom()).isNull();
        assertThat(request.dateTo()).isNull();
    }

    @Test
    @DisplayName("cashier field holds the supplied value")
    void cashierField_holdsCashierName() {
        CashBalanceRequest request = new CashBalanceRequest("MARTINA", null, null);

        assertThat(request.cashier()).isEqualTo("MARTINA");
    }

    @Test
    @DisplayName("dateFrom and dateTo fields hold the supplied LocalDate values")
    void dateFields_holdLocalDates() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to   = LocalDate.of(2025, 12, 31);

        CashBalanceRequest request = new CashBalanceRequest(null, from, to);

        assertThat(request.dateFrom()).isEqualTo(from);
        assertThat(request.dateTo()).isEqualTo(to);
    }

    @Test
    @DisplayName("all-populated constructor sets all three fields independently")
    void allPopulated_fieldsIndependent() {
        LocalDate from = LocalDate.of(2025, 4, 1);
        LocalDate to   = LocalDate.of(2025, 4, 30);

        CashBalanceRequest request = new CashBalanceRequest("PETER", from, to);

        assertThat(request.cashier()).isEqualTo("PETER");
        assertThat(request.dateFrom()).isEqualTo(from);
        assertThat(request.dateTo()).isEqualTo(to);
    }

    @Test
    @DisplayName("two records with identical values are equal (record semantics)")
    void equalRecords_areEqual() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to   = LocalDate.of(2025, 12, 31);

        CashBalanceRequest a = new CashBalanceRequest("LINDA", from, to);
        CashBalanceRequest b = new CashBalanceRequest("LINDA", from, to);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
