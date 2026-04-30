package bg.fibank.cashdesk.repository;

import bg.fibank.cashdesk.config.AppProperties;
import bg.fibank.cashdesk.model.Denomination;
import bg.fibank.cashdesk.model.Transaction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static bg.fibank.cashdesk.model.Currency.BGN;
import static bg.fibank.cashdesk.model.Currency.EUR;
import static bg.fibank.cashdesk.model.OperationType.DEPOSIT;
import static bg.fibank.cashdesk.model.OperationType.WITHDRAWAL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TransactionFileRepository}.
 *
 * <p>No Spring context is started. The repository is wired with a temp-dir
 * file path and {@code init()} is called manually before each test.</p>
 */
class TransactionFileRepositoryTest {

    @TempDir
    Path tempDir;

    private TransactionFileRepository repo;
    private Path txFile;

    @BeforeEach
    void setUp() throws IOException {
        txFile = tempDir.resolve("transactions.txt");

        AppProperties props = new AppProperties();
        props.getData().setBalancesFile(tempDir.resolve("cash_balances.txt").toString());
        props.getData().setTransactionsFile(txFile.toString());
        props.getAuth().setApiKey("test-key");

        repo = new TransactionFileRepository(props);
        repo.init();
    }


    @Test
    @DisplayName("init() creates transaction file with header when it does not exist")
    void init_createsFile_withHeader() throws IOException {
        assertThat(txFile).exists();
        String content = Files.readString(txFile);
        assertThat(content).startsWith(FileFormat.COMMENT_PREFIX);
    }


    @Test
    @DisplayName("append() writes one data line per transaction")
    void append_writesOneLine() throws IOException {
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 29, 10, 0, 0)));

        long dataLines = Files.readAllLines(txFile).stream()
                .filter(l -> !FileFormat.isSkippable(l))
                .count();
        assertThat(dataLines).isEqualTo(1);
    }

    @Test
    @DisplayName("append() is cumulative — each call adds exactly one more line")
    void append_cumulative() throws IOException {
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 29, 10, 0, 0)));
        repo.append(withdrawalEur("PETER",  LocalDateTime.of(2025, 4, 29, 10, 5, 0)));
        repo.append(depositBgn("LINDA",   LocalDateTime.of(2025, 4, 29, 10, 10, 0)));

        long dataLines = Files.readAllLines(txFile).stream()
                .filter(l -> !FileFormat.isSkippable(l))
                .count();
        assertThat(dataLines).isEqualTo(3);
    }

    @Test
    @DisplayName("append() then findAll() round-trip preserves all transaction fields")
    void append_findAll_roundTrip() {
        LocalDateTime ts = LocalDateTime.of(2025, 4, 29, 10, 0, 0);
        Transaction original = depositBgn("MARTINA", ts);

        repo.append(original);
        List<Transaction> results = repo.findAll(null, null, null);

        assertThat(results).hasSize(1);
        Transaction loaded = results.get(0);
        assertThat(loaded.timestamp()).isEqualTo(ts);
        assertThat(loaded.cashierName()).isEqualTo("MARTINA");
        assertThat(loaded.operationType()).isEqualTo(DEPOSIT);
        assertThat(loaded.currency()).isEqualTo(BGN);
        assertThat(loaded.amount()).isEqualTo(600);
        assertThat(loaded.denominations()).hasSize(2);
    }


    @Test
    @DisplayName("findAll() with no filters returns all appended transactions")
    void findAll_noFilters_returnsAll() {
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 1, 9, 0, 0)));
        repo.append(withdrawalEur("PETER",  LocalDateTime.of(2025, 4, 2, 9, 0, 0)));
        repo.append(depositBgn("LINDA",   LocalDateTime.of(2025, 4, 3, 9, 0, 0)));

        assertThat(repo.findAll(null, null, null)).hasSize(3);
    }

    @Test
    @DisplayName("findAll() returns empty list when no transactions have been appended")
    void findAll_empty_returnsEmptyList() {
        assertThat(repo.findAll(null, null, null)).isEmpty();
    }


    @Test
    @DisplayName("findAll() cashier filter returns only matching cashier transactions")
    void findAll_cashierFilter_matchesExact() {
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 1, 9, 0, 0)));
        repo.append(depositBgn("PETER",   LocalDateTime.of(2025, 4, 1, 9, 5, 0)));

        List<Transaction> results = repo.findAll("MARTINA", null, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).cashierName()).isEqualTo("MARTINA");
    }

    @Test
    @DisplayName("findAll() cashier filter is case-insensitive")
    void findAll_cashierFilter_caseInsensitive() {
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 1, 9, 0, 0)));

        assertThat(repo.findAll("martina", null, null)).hasSize(1);
        assertThat(repo.findAll("Martina", null, null)).hasSize(1);
    }

    @Test
    @DisplayName("findAll() cashier filter returns empty when no match")
    void findAll_cashierFilter_noMatch_returnsEmpty() {
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 1, 9, 0, 0)));

        assertThat(repo.findAll("LINDA", null, null)).isEmpty();
    }


    @Test
    @DisplayName("findAll() dateFrom filter excludes transactions before the date")
    void findAll_dateFrom_excludesBefore() {
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 1, 9, 0, 0)));
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 5, 9, 0, 0)));
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 10, 9, 0, 0)));

        List<Transaction> results = repo.findAll(null, LocalDate.of(2025, 4, 5), null);
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("findAll() dateTo filter excludes transactions after the date")
    void findAll_dateTo_excludesAfter() {
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 1, 9, 0, 0)));
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 5, 9, 0, 0)));
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 10, 9, 0, 0)));

        List<Transaction> results = repo.findAll(null, null, LocalDate.of(2025, 4, 5));
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("findAll() dateFrom and dateTo are both inclusive")
    void findAll_dateRange_inclusive() {
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 1, 0, 0, 0)));  // boundary start
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 3, 12, 0, 0))); // inside
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 5, 23, 59, 59))); // boundary end
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 6, 0, 0, 0)));  // outside

        List<Transaction> results = repo.findAll(
                null, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 5));
        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("findAll() combined cashier + dateRange filter works correctly")
    void findAll_combinedFilter() {
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 1, 9, 0, 0)));
        repo.append(depositBgn("PETER",   LocalDateTime.of(2025, 4, 1, 9, 0, 0)));
        repo.append(depositBgn("MARTINA", LocalDateTime.of(2025, 4, 10, 9, 0, 0)));

        List<Transaction> results = repo.findAll(
                "MARTINA",
                LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 5));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).cashierName()).isEqualTo("MARTINA");
    }


    @Test
    @DisplayName("findAll() skips malformed lines without throwing")
    void findAll_malformedLines_skipped() throws IOException {
        Files.writeString(txFile,
                FileFormat.TX_HEADER + "\n"
                        + "2025-04-29T10:00:00|MARTINA|DEPOSIT|BGN|600|10x10,50x10\n"
                        + "CORRUPTED_LINE\n"
                        + "2025-04-29T10:05:00|PETER|WITHDRAWAL|EUR|500|50x10\n");

        List<Transaction> results = repo.findAll(null, null, null);
        assertThat(results).hasSize(2);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Transaction depositBgn(String cashier, LocalDateTime ts) {
        return Transaction.of(cashier, DEPOSIT, BGN, 600,
                List.of(new Denomination(10, 10), new Denomination(50, 10)), ts);
    }

    private Transaction withdrawalEur(String cashier, LocalDateTime ts) {
        return Transaction.of(cashier, WITHDRAWAL, EUR, 500,
                List.of(new Denomination(50, 10)), ts);
    }
}
