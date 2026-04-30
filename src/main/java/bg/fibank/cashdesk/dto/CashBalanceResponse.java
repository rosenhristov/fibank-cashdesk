package bg.fibank.cashdesk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashBalanceResponse {
    private List<CashierBalanceEntry> cashiers;
}
