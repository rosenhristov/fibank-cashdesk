package bg.fibank.cashdesk.dto;

import bg.fibank.cashdesk.model.Currency;
import bg.fibank.cashdesk.model.Denomination;
import bg.fibank.cashdesk.model.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashOperationResponse {

    private String cashierName;
    private Map<Currency, Integer> totals;
    private Map<Currency, List<Denomination>> denominations;
    private List<Transaction> history;

}
