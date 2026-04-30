package bg.fibank.cashdesk.dto;

import bg.fibank.cashdesk.model.Currency;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashierBalanceEntry {
    private String cashierName;
    private Map<Currency, Integer> totals;
    private Map<Currency, List<DenominationDto>> denominations;
}
