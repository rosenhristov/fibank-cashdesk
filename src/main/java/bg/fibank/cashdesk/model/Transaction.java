package bg.fibank.cashdesk.model;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;


/**
 * Immutable record of a single completed cash operation.
 *
 * <p>Instances are created by {@code CashDeskService} after a deposit or
 * withdrawal succeeds and are handed to {@code TransactionFileRepository}
 * for persistence in the append-only {@code transactions.txt} log.</p>
 *
 * <h2>Denomination snapshot</h2>
 * <p>The {@code denominations} list captures the bills involved in <em>this
 * operation</em>, not the cashier's total balance after it.  Each
 * {@link Denomination} is a defensive copy taken at operation time so that
 * subsequent balance mutations cannot alter the historical record.</p>
 *
 * <h2>File serialisation contract</h2>
 * <p>The pipe-delimited line format written by the repository is:</p>
 * <pre>
 *   TIMESTAMP|CASHIER|TYPE|CURRENCY|AMOUNT|DENOMINATIONS
 *   2025-04-29T10:00:00|MARTINA|DEPOSIT|BGN|600|10x10,10x50
 * </pre>
 * <p>The {@link #denominationsAsString()} helper produces the compact
 * {@code faceValue×count} comma-separated representation used in that column.</p>
 */
public record Transaction(String cashierName,
                          OperationType operationType,
                          Currency currency,
                          int amount,
                          List<Denomination> denominations,
                          LocalDateTime timestamp) {

    /**
     * Defensive compact constructor: wraps {@code denominations} in an
     * unmodifiable list so the record truly cannot be mutated after creation,
     * even if the caller holds a reference to the original mutable list.
     */
    public Transaction {
        denominations = denominations != null ? List.copyOf(denominations) : List.of();
    }

    /**
     * Convenience factory used by {@code CashDeskService}.  Named parameters
     * replace the Lombok {@code @Builder} that would be needed on a class.
     */
    public static Transaction of(String cashierName, OperationType operationType,
                                 Currency currency, int amount,
                                 List<Denomination> denominations, LocalDateTime timestamp) {
        return new Transaction(cashierName, operationType, currency,
                               amount, denominations, timestamp);
    }

    /**
     * Returns the denominations in pipe-file compact format, e.g. {@code "10x10,10x50"}.
     * Used by {@code TransactionFileRepository} when writing the log line.
     */
    public String denominationsAsString() {
        if (denominations.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < denominations.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(denominations.get(i).getFaceValue())
                    .append('x')
                    .append(denominations.get(i).getCount());
        }
        return sb.toString();
    }

    /**
     * Parses a compact denomination string (as written by {@link #denominationsAsString()})
     * back into a list of {@link Denomination} objects.
     *
     * <p>Used by {@code TransactionFileRepository} when reading the log file.</p>
     *
     * @param raw a string in the form {@code "10x10,10x50"}, or empty/null
     * @return parsed list, or an empty list if {@code raw} is blank
     */
    public static List<Denomination> parseDenominations(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    String[] parts = s.split("x", 2);
                    int faceValue  = Integer.parseInt(parts[0]);
                    int count      = Integer.parseInt(parts[1]);
                    return new Denomination(faceValue, count);
                })
                .toList();
    }
}
