package bg.fibank.cashdesk.dto;

import java.util.List;
import java.time.LocalDate;

/**
 * Response body for {@code GET /api/v1/cash-balance}.
 *
 * <p>Contains one {@link CashierBalanceDto} per cashier that matched the
 * query filters. When no filters are supplied all three cashiers are returned
 * in insertion order (MARTINA, PETER, LINDA).</p>
 *
 * @param cashiers  ordered list of cashier balance entries
 * @param dateFrom  the {@code dateFrom} filter that was applied, or {@code null}
 * @param dateTo    the {@code dateTo} filter that was applied, or {@code null}
 * @param cashier   the cashier filter that was applied, or {@code null}
 */
public record CashBalanceResponse(

        List<CashierBalanceDto> cashiers,
        String cashier,
        LocalDate dateFrom,
        LocalDate dateTo) {

}
