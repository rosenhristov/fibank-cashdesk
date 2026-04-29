package bg.fibank.cashdesk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Transaction(String cashierName,
                          OperationType type,
                          Currency currency,
                          BigDecimal amount,
                          List<Denomination> denominations,
                          LocalDateTime timestamp) {

}
