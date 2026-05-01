package bg.fibank.cashdesk.repository;

import bg.fibank.cashdesk.config.AppProperties;
import bg.fibank.cashdesk.exception.FileFormatException;
import bg.fibank.cashdesk.model.Transaction;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Persists and queries the cash operation history using {@code transactions.txt}.
 *
 * <h2>Write model — append only</h2>
 * <p>Every completed operation adds exactly one line to the end of the file.
 * Existing lines are never modified or deleted. This makes the file a reliable
 * audit log: the worst that can happen on a crash is a missing final line,
 * never a corrupted earlier entry.</p>
 *
 * <h2>Read model — full scan with in-memory filter</h2>
 * <p>Queries read all non-skippable lines from disk, decode each one, and
 * apply the caller-supplied filters in memory. For the transaction volumes
 * expected of a three-cashier cash desk this is fast enough; a database index
 * would only be warranted at several hundred thousand rows.</p>
 *
 * <h2>Thread safety</h2>
 * <p>{@link #append} is {@code synchronized} on {@code this} so concurrent
 * service calls never interleave a partial line write. Reads ({@link #findAll})
 * are not synchronised — they open a fresh read stream on a file that is only
 * ever grown by appending complete lines, so readers always see a valid
 * (though possibly stale-by-one) snapshot.</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TransactionFileRepository {

    private final AppProperties appProperties;

    private Path transactionsPath;

    // ── lifecycle ─────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() throws IOException {
        transactionsPath = Path.of(appProperties.getData().getTransactionsFile());
        ensureFileExists(transactionsPath);
        log.info("TRANSACTION_REPO | Initialised | file={}", transactionsPath);
    }

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Appends a completed transaction as a single pipe-delimited line.
     *
     * <p>The write is synchronised on {@code this} to prevent interleaved
     * partial-line writes from concurrent requests.</p>
     *
     * @param tx the transaction to persist; must not be {@code null}
     * @throws UncheckedIOException if the file cannot be written
     */
    public synchronized void append(Transaction tx) {
        String line = FileFormat.encodeTransactionLine(tx);
        try (BufferedWriter writer = Files.newBufferedWriter(
                transactionsPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {

            writer.write(line);
            writer.newLine();

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to append transaction for cashier " + tx.cashierName(), e);
        }

        log.info("TRANSACTION_REPO | Appended | cashier={} | type={} | currency={} | amount={}",
                tx.cashierName(), tx.operationType(), tx.currency(), tx.amount());
    }

    /**
     * Returns all transactions, optionally filtered by cashier name and/or
     * date range. All parameters are optional — pass {@code null} to skip a filter.
     *
     * <p>Filtering is applied in this order:</p>
     * <ol>
     *   <li>Cashier name — case-insensitive exact match</li>
     *   <li>Date from   — transaction date &ge; {@code dateFrom} (inclusive)</li>
     *   <li>Date to     — transaction date &le; {@code dateTo}   (inclusive)</li>
     * </ol>
     *
     * @param cashierName optional cashier name filter (case-insensitive)
     * @param dateFrom    optional inclusive start date
     * @param dateTo      optional inclusive end date
     * @return matching transactions in file order (chronological)
     */
    public List<Transaction> findAll(String cashierName,
                                     LocalDate dateFrom,
                                     LocalDate dateTo) {
        List<String> lines;
        try {
            lines = Files.readAllLines(transactionsPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read transaction file", e);
        }

        return lines.stream()
                .filter(line -> !FileFormat.isSkippable(line))
                .map(line -> {
                    try {
                        return FileFormat.decodeTransactionLine(line);
                    } catch (FileFormatException e) {
                        log.warn("TRANSACTION_REPO | Skipping malformed line: {} | error: {}",
                                line, e.getMessage());
                        return null;
                    }
                })
                .filter(tx -> tx != null)
                .filter(tx -> cashierName == null
                        || tx.cashierName().equalsIgnoreCase(cashierName))
                .filter(tx -> dateFrom == null
                        || !tx.timestamp().toLocalDate().isBefore(dateFrom))
                .filter(tx -> dateTo == null
                        || !tx.timestamp().toLocalDate().isAfter(dateTo))
                .toList();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Creates the transaction file (with the header comment) if it does not exist.
     * Parent directories are also created as needed.
     */
    private void ensureFileExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, FileFormat.TX_HEADER + System.lineSeparator());
            log.info("TRANSACTION_REPO | Created new transaction file | path={}", path);
        }
    }
}