package bg.fibank.cashdesk.dto;

import bg.fibank.cashdesk.model.Currency;
import bg.fibank.cashdesk.model.OperationType;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/cash-operation}.
 *
 * <p>Returns the cashier's updated state for the affected currency only,
 * so the caller can immediately verify the new balance without issuing a
 * separate balance query.</p>
 *
 * @param cashierName   name of the cashier who performed the operation
 * @param operationType the operation that was applied
 * @param currency      the currency that was affected
 * @param newBalance    total balance for {@link #currency} after the operation
 * @param denominations updated denomination breakdown for {@link #currency}
 */
public record CashOperationResponse(

        String cashierName,
        OperationType operationType,
        Currency currency,
        int newBalance,
        List<DenominationDto> denominations) {

}

