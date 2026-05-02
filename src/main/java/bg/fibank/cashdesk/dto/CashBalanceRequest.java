package bg.fibank.cashdesk.dto;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

/**
 * Query parameters for {@code GET /api/v1/cash-balance}.
 *
 * <p>All three fields are optional — omitting any of them simply widens the result set. Spring MVC binds the
 * incoming query string to this record via {@code @ModelAttribute} using the canonical constructor introduced
 * in Spring Framework 6.1.</p>
 *
 * <p>Field names match the public query-parameter names exactly so the URL
 * contract is preserved:</p>
 * <pre>
 *   GET /api/v1/cash-balance?cashier=MARTINA&amp;dateFrom=2025-01-01&amp;dateTo=2025-12-31
 * </pre>
 *
 * <h2>Why no {@code @NotBlank} on {@code cashier}?</h2>
 * <p>The spec requires all three parameters to be optional. Adding {@code @NotBlank} would make
 * {@code cashier} mandatory and break requests that omit it to query all cashiers at once.</p>
 *
 * @param cashier  optional case-insensitive cashier name filter (MARTINA, PETER, LINDA)
 * @param dateFrom optional inclusive start date; parsed from {@code yyyy-MM-dd}
 * @param dateTo   optional inclusive end date; parsed from {@code yyyy-MM-dd}
 */
public record CashBalanceRequest(

        String cashier,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateTo) {

}

