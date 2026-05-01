package bg.fibank.cashdesk.dto;

/**
 * Complete balance snapshot for a single cashier, included in the
 * {@code GET /api/v1/cash-balance} response.
 *
 * @param cashierName name of the cashier
 * @param bgn         live BGN balance with denominations
 * @param eur         live EUR balance with denominations
 */
public record CashierBalanceDto(
        String cashierName,
        CurrencyBalanceDto bgn,
        CurrencyBalanceDto eur
) {}
