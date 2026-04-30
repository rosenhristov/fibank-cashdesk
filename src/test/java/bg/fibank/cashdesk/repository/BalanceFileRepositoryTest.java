package bg.fibank.cashdesk.repository;

import bg.fibank.cashdesk.config.AppProperties;
import bg.fibank.cashdesk.model.CashierBalance;
import bg.fibank.cashdesk.model.Currency;
import bg.fibank.cashdesk.model.Denomination;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BalanceFileRepository}.
 *
 * <p>No Spring context is started — the repository is constructed directly
 * with an {@link AppProperties} instance pointing at a {@code @TempDir} file,
 * and {@code init()} is called manually. This keeps the test fast and avoids
 * classpath scanning overhead.</p>
 */
class BalanceFileRepositoryTest {

    @TempDir
    Path tempDir;

    private BalanceFileRepository repo;
    private Path balancesFile;

    @BeforeEach
    void setUp() throws IOException {
        balancesFile = tempDir.resolve("cash_balances.txt");

        AppProperties props = new AppProperties();
        props.getData().setBalancesFile(balancesFile.toString());
        props.getData().setTransactionsFile(tempDir.resolve("transactions.txt").toString());
        props.getAuth().setApiKey("test-key");

        repo = new BalanceFileRepository(props);
        repo.init();
    }

    @Test
    @DisplayName("init() creates the balance file with header comment when it does not exist")
    void init_createsFile_withHeader() throws IOException {
        assertThat(balancesFile).exists();
        String content = Files.readString(balancesFile);
        assertThat(content).startsWith(FileFormat.COMMENT_PREFIX);
    }

    @Test
    @DisplayName("init() on an existing file does not wipe its data")
    void init_existingFile_dataIsPreserved() throws IOException {
        // seed file with one balance row
        Files.writeString(balancesFile, FileFormat.BAL_HEADER + "\nMARTINA|BGN|10|50\n");

        // re-init same repo (simulates restart)
        repo.init();

        Optional<CashierBalance> result = repo.findByCashier("MARTINA");
        assertThat(result).isPresent();
        assertThat(result.get().getTotalForCurrency(Currency.BGN)).isEqualTo(500);
    }


    @Test
    @DisplayName("findAll() returns empty list when no cashiers have been saved")
    void findAll_empty_returnsEmptyList() {
        assertThat(repo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("findByCashier() returns empty when cashier does not exist")
    void findByCashier_unknown_returnsEmpty() {
        assertThat(repo.findByCashier("NOBODY")).isEmpty();
    }

    @Test
    @DisplayName("findByCashier() is case-insensitive")
    void findByCashier_caseInsensitive() throws IOException {
        repo.saveInitial(martina());

        assertThat(repo.findByCashier("MARTINA")).isPresent();
        assertThat(repo.findByCashier("martina")).isPresent();
        assertThat(repo.findByCashier("Martina")).isPresent();
    }

    @Test
    @DisplayName("exists() returns false for unknown cashier")
    void exists_unknown_returnsFalse() {
        assertThat(repo.exists("GHOST")).isFalse();
    }

    @Test
    @DisplayName("exists() returns true after saving a cashier")
    void exists_afterSave_returnsTrue() throws IOException {
        repo.saveInitial(martina());
        assertThat(repo.exists("MARTINA")).isTrue();
    }


    @Test
    @DisplayName("save() persists a cashier and is immediately readable via findByCashier()")
    void save_persistsAndReadable() throws IOException {
        CashierBalance martina = martina();
        repo.save(martina);

        Optional<CashierBalance> loaded = repo.findByCashier("MARTINA");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getTotalForCurrency(Currency.BGN)).isEqualTo(1000);
        assertThat(loaded.get().getTotalForCurrency(Currency.EUR)).isEqualTo(2000);
    }

    @Test
    @DisplayName("save() writes all three cashiers to the file in findAll() order")
    void save_multiCashier_allPersisted() throws IOException {
        repo.saveInitial(martina());
        repo.saveInitial(peter());
        repo.saveInitial(linda());

        assertThat(repo.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("save() overwrites a prior save — no duplicate rows in file")
    void save_overwrite_noDuplicateRows() throws IOException {
        CashierBalance martina = martina();
        repo.save(martina);

        // simulate a deposit: add 10 × 10-BGN notes
        martina.addDenominations(Currency.BGN, List.of(new Denomination(10, 10)));
        repo.save(martina);

        // only one MARTINA|BGN|10 row should exist
        long count = Files.readAllLines(balancesFile).stream()
                .filter(l -> l.startsWith("MARTINA|BGN|10|"))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("full round-trip: save all three cashiers, restart, all balances intact")
    void roundTrip_restartPreservesAllBalances() throws IOException {
        repo.saveInitial(martina());
        repo.saveInitial(peter());
        repo.saveInitial(linda());

        // simulate restart — new repo instance over the same file
        AppProperties props = new AppProperties();
        props.getData().setBalancesFile(balancesFile.toString());
        props.getData().setTransactionsFile(tempDir.resolve("transactions.txt").toString());
        props.getAuth().setApiKey("test-key");

        BalanceFileRepository freshRepo = new BalanceFileRepository(props);
        freshRepo.init();

        assertThat(freshRepo.findByCashier("MARTINA").get().getTotalForCurrency(Currency.BGN)).isEqualTo(1000);
        assertThat(freshRepo.findByCashier("PETER").get().getTotalForCurrency(Currency.EUR)).isEqualTo(2000);
        assertThat(freshRepo.findByCashier("LINDA").get().getTotalForCurrency(Currency.BGN)).isEqualTo(1000);
    }

    @Test
    @DisplayName("file contains header comment after save")
    void save_fileContainsHeader() throws IOException {
        repo.save(martina());
        String first = Files.readAllLines(balancesFile).get(0);
        assertThat(first).startsWith(FileFormat.COMMENT_PREFIX);
    }

    @Test
    @DisplayName("denomination order in file is sorted ascending by face value")
    void save_denominationRowsSortedByFaceValue() throws IOException {
        // martina() seeds 50-BGN before 10-BGN — save must sort them
        repo.save(martina());

        List<String> rows = Files.readAllLines(balancesFile).stream()
                .filter(l -> l.startsWith("MARTINA|BGN|"))
                .toList();

        // should be 10 before 50
        assertThat(rows.get(0)).contains("|10|");
        assertThat(rows.get(1)).contains("|50|");
    }

    @Test
    @DisplayName("malformed lines in the file are skipped without exception on init")
    void init_malformedLine_skipped() throws IOException {
        Files.writeString(balancesFile,
                FileFormat.BAL_HEADER + "\n"
                        + "MARTINA|BGN|10|50\n"
                        + "THIS_IS_GARBAGE\n"      // bad line — should be skipped
                        + "PETER|EUR|50|20\n");

        repo.init();

        assertThat(repo.findByCashier("MARTINA")).isPresent();
        assertThat(repo.findByCashier("PETER")).isPresent();
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private CashierBalance martina() {
        CashierBalance b = new CashierBalance("MARTINA");
        b.addDenominations(Currency.BGN, List.of(new Denomination(10, 50), new Denomination(50, 10)));
        b.addDenominations(Currency.EUR, List.of(new Denomination(10, 100), new Denomination(50, 20)));
        return b;
    }

    private CashierBalance peter() {
        CashierBalance b = new CashierBalance("PETER");
        b.addDenominations(Currency.BGN, List.of(new Denomination(10, 50), new Denomination(50, 10)));
        b.addDenominations(Currency.EUR, List.of(new Denomination(10, 100), new Denomination(50, 20)));
        return b;
    }

    private CashierBalance linda() {
        CashierBalance b = new CashierBalance("LINDA");
        b.addDenominations(Currency.BGN, List.of(new Denomination(10, 50), new Denomination(50, 10)));
        b.addDenominations(Currency.EUR, List.of(new Denomination(10, 100), new Denomination(50, 20)));
        return b;
    }
}
 
