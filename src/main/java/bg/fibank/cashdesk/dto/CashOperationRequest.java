package bg.fibank.cashdesk.dto;

import bg.fibank.cashdesk.model.Currency;
import bg.fibank.cashdesk.model.Denomination;
import bg.fibank.cashdesk.model.OperationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashOperationRequest {

    @NotBlank(message = "Cashier name is required")
    private String cashierName;

    @NotNull(message = "Operation type is required")
    private OperationType operationType;

    @NotNull(message = "Currency is required")
    private Currency currency;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotEmpty(message = "At least one denomination must be provided")
    @Valid
    private List<DenominationDto> denominations;

}
