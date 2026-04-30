package bg.fibank.cashdesk.repository;


import bg.fibank.cashdesk.exception.FileFormatException;
import bg.fibank.cashdesk.model.Currency;
import bg.fibank.cashdesk.model.Denomination;
import bg.fibank.cashdesk.model.OperationType;
import bg.fibank.cashdesk.model.Transaction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static java.util.Objects.isNull;

/**
 * Single source of truth for the pipe-delimited text file formats used by
 * {@code BalanceFileRepository} and {@code TransactionFileRepository}.
 *
 * <p>This class owns <strong>everything</strong> that touches raw file bytes:
 * delimiters, column indices, the timestamp pattern, comment syntax, and the
 * encode/decode methods that convert between domain objects and file lines.
 * Neither repository contains any format literals — they delegate entirely to
 * the static methods here.</p>
 *
 * <h2>Why pipe-delimited plain text?</h2>
 * <ul>
 *   <li>No parsing library required — {@code String.split} is sufficient.</li>
 *   <li>{@code |} never appears in cashier names, currency codes, integers, or
 *       ISO timestamps, so no escaping is needed.</li>
 *   <li>Human-readable without tooling; diffable in version control.</li>
 *   <li>Append-only writes to the transaction log are a single
 *       {@code BufferedWriter.newLine()} call — no JSON object or XML tag to
 *       close.</li>
 * </ul>
 *
 * <h2>Balance file — {@code cash_balances.txt}</h2>
 * <p>One row per denomination slot. The file is <em>rewritten in full</em>
 * after every operation (write-to-tmp, then atomic rename).</p>
 * <pre>
 * # CASHIER|CURRENCY|FACE_VALUE|COUNT
 * MARTINA|BGN|10|50
 * MARTINA|BGN|50|10
 * MARTINA|EUR|10|100
 * MARTINA|EUR|20|0
 * MARTINA|EUR|50|20
 * PETER|BGN|10|50
 * ...
 * </pre>
 *
 * <h2>Transaction file — {@code transactions.txt}</h2>
 * <p>One row per completed operation, always appended, never modified.</p>
 * <pre>
 * # TIMESTAMP|CASHIER|TYPE|CURRENCY|AMOUNT|DENOMINATIONS
 * 2025-04-29T10:00:00|MARTINA|DEPOSIT|BGN|600|10x10,10x50
 * 2025-04-29T10:05:00|PETER|WITHDRAWAL|EUR|500|50x10
 * </pre>
 */
public class FileFormat {

    private FileFormat() {}

    // ── shared constants ──────────────────────────────────────────────────────

    /** Column separator. Must not appear in any field value. */
    public static final String DELIMITER = "|";

    /** Regex-safe split pattern for {@link String#split}. */
    public static final String DELIMITER_REGEX = "\\|";

    /** Lines whose first non-whitespace character is {@code #} are skipped. */
    public static final String COMMENT_PREFIX  = "#";

    /**
     * Timestamp format used in the transaction log.
     * Seconds precision is enough; sub-second granularity adds noise.
     * No timezone suffix because the module runs in one local timezone.
     */
    public static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── balance file column indices ───────────────────────────────────────────

    public static final int BAL_COL_CASHIER    = 0;
    public static final int BAL_COL_CURRENCY   = 1;
    public static final int BAL_COL_FACE_VALUE = 2;
    public static final int BAL_COL_COUNT      = 3;
    public static final int BAL_COL_COUNT_TOTAL = 4;

    /** Header written once at the top of a freshly created balance file. */
    public static final String BAL_HEADER = "# CASHIER|CURRENCY|FACE_VALUE|COUNT";

    // ── transaction file column indices ───────────────────────────────────────

    public static final int TX_COL_TIMESTAMP     = 0;
    public static final int TX_COL_CASHIER       = 1;
    public static final int TX_COL_TYPE          = 2;
    public static final int TX_COL_CURRENCY      = 3;
    public static final int TX_COL_AMOUNT        = 4;
    public static final int TX_COL_DENOMINATIONS = 5;
    public static final int TX_COL_COUNT_TOTAL   = 6;

    /** Header written once at the top of a freshly created transaction file. */
    public static final String TX_HEADER = "# TIMESTAMP|CASHIER|TYPE|CURRENCY|AMOUNT|DENOMINATIONS";

    // ── line predicates ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} for lines that should be skipped during parsing:
     * blank lines and comment lines (starting with {@value #COMMENT_PREFIX}).
     */
    public static boolean isSkippable(String line) {
        if (isNull(line)) {
            return true;
        }
        String trimmed = line.strip();

        return trimmed.isEmpty() || trimmed.startsWith(COMMENT_PREFIX);
    }

    // ── balance file codec ────────────────────────────────────────────────────

    /**
     * Encodes one denomination slot as a balance-file line.
     *
     * <p>Example output: {@code "MARTINA|BGN|10|50"}</p>
     *
     * @param cashierName   name of the cashier (e.g. {@code "MARTINA"})
     * @param currency      the currency of this denomination
     * @param denomination  the denomination to encode
     * @return a single pipe-delimited line, no trailing newline
     */
    public static String encodeBalanceLine(String cashierName, Currency currency,
                                           Denomination denomination) {
        return cashierName
                + DELIMITER + currency.name()
                + DELIMITER + denomination.getFaceValue()
                + DELIMITER + denomination.getCount();
    }

    /**
     * Decodes one balance-file line into its four components.
     *
     * <p>The caller is responsible for skipping comment/blank lines via
     * {@link #isSkippable(String)} before calling this method.</p>
     *
     * @param line a raw line from {@code cash_balances.txt}
     * @return {@link BalanceLine} record with parsed fields
     * @throws FileFormatException if the line has fewer than
     *                             {@value #BAL_COL_COUNT_TOTAL} columns or
     *                             contains unparseable integers
     */
    public static BalanceLine decodeBalanceLine(String line) {
        String[] cols = line.split(DELIMITER_REGEX, -1);
        if (cols.length < BAL_COL_COUNT_TOTAL) {
            throw new FileFormatException(
                    "Balance line has " + cols.length + " column(s), expected "
                            + BAL_COL_COUNT_TOTAL + ": [" + line + "]");
        }
        try {
            String   cashierName = cols[BAL_COL_CASHIER].strip();
            Currency currency    = Currency.valueOf(cols[BAL_COL_CURRENCY].strip());
            int      faceValue   = Integer.parseInt(cols[BAL_COL_FACE_VALUE].strip());
            int      count       = Integer.parseInt(cols[BAL_COL_COUNT].strip());
            return new BalanceLine(cashierName, currency, faceValue, count);
        } catch (IllegalArgumentException e) {
            throw new FileFormatException("Cannot parse balance line: [" + line + "]", e);
        }
    }

    /**
     * Parsed representation of one line in {@code cash_balances.txt}.
     *
     * @param cashierName name of the cashier
     * @param currency    the currency
     * @param faceValue   denomination face value
     * @param count       number of bills of this denomination
     */
    public record BalanceLine(String cashierName, Currency currency,
                              int faceValue, int count) {}

    // ── transaction file codec ────────────────────────────────────────────────

    /**
     * Encodes a completed {@link Transaction} as a transaction-log line.
     *
     * <p>Example output:</p>
     * <pre>2025-04-29T10:00:00|MARTINA|DEPOSIT|BGN|600|10x10,10x50</pre>
     *
     * @param tx the transaction to encode
     * @return a single pipe-delimited line, no trailing newline
     */
    public static String encodeTransactionLine(Transaction tx) {
        return tx.timestamp().format(TIMESTAMP_FORMATTER)
                + DELIMITER + tx.cashierName()
                + DELIMITER + tx.operationType().name()
                + DELIMITER + tx.currency().name()
                + DELIMITER + tx.amount()
                + DELIMITER + tx.denominationsAsString();
    }

    /**
     * Decodes one transaction-log line into a {@link Transaction} record.
     *
     * <p>The caller is responsible for skipping comment/blank lines via
     * {@link #isSkippable(String)} before calling this method.</p>
     *
     * @param line a raw line from {@code transactions.txt}
     * @return the decoded {@link Transaction}
     * @throws FileFormatException if the line has fewer than
     *                             {@value #TX_COL_COUNT_TOTAL} columns, contains
     *                             an unparseable timestamp, or references an
     *                             unknown enum constant
     */
    public static Transaction decodeTransactionLine(String line) {
        String[] cols = line.split(DELIMITER_REGEX, -1);
        if (cols.length < TX_COL_COUNT_TOTAL) {
            throw new FileFormatException(
                    "Transaction line has " + cols.length + " column(s), expected "
                            + TX_COL_COUNT_TOTAL + ": [" + line + "]");
        }
        try {
            LocalDateTime timestamp   = LocalDateTime.parse(
                    cols[TX_COL_TIMESTAMP].strip(), TIMESTAMP_FORMATTER);
            String        cashierName = cols[TX_COL_CASHIER].strip();
            OperationType type        = OperationType.valueOf(cols[TX_COL_TYPE].strip());
            Currency      currency    = Currency.valueOf(cols[TX_COL_CURRENCY].strip());
            int           amount      = Integer.parseInt(cols[TX_COL_AMOUNT].strip());
            var           denoms      = Transaction.parseDenominations(cols[TX_COL_DENOMINATIONS].strip());

            return Transaction.of(cashierName, type, currency, amount, denoms, timestamp);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new FileFormatException("Cannot parse transaction line: [" + line + "]", e);
        }
    }
}
