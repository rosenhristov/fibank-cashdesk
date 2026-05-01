package bg.fibank.cashdesk.dto;

import bg.fibank.cashdesk.model.Currency;
import bg.fibank.cashdesk.model.OperationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/cash-operation}.
 *
 * <p>A single endpoint handles both deposits and withdrawals; the
 * {@link #operationType} field distinguishes them. Validation annotations
 * are enforced by {@code @Valid} on the controller parameter (Phase 7).</p>
 *
 * @param cashierName   name of the cashier performing the operation
 * @param operationType DEPOSIT or WITHDRAWAL
 * @param currency      BGN or EUR
 * @param amount        total monetary amount; must equal the sum of
 *                      {@code faceValue × count} across all {@link #denominations}
 * @param denominations breakdown of bills involved in the operation
 */
public record CashOperationRequest(

        @NotBlank(message = "cashierName must not be blank")
        String cashierName,

        @NotNull(message = "operationType must not be null (DEPOSIT or WITHDRAWAL)")
        OperationType operationType,

        @NotNull(message = "currency must not be null (BGN or EUR)")
        Currency currency,

        @Positive(message = "amount must be a positive integer")
        int amount,

        @NotEmpty(message = "denominations must not be empty")
        @Valid
        List<DenominationDto> denominations
) {}
