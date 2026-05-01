package bg.fibank.cashdesk.init;

import bg.fibank.cashdesk.config.AppProperties;
import bg.fibank.cashdesk.model.CashierBalance;
import bg.fibank.cashdesk.model.Denomination;
import bg.fibank.cashdesk.repository.BalanceFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.IOException;
import java.nio.file.Path;

import static bg.fibank.cashdesk.model.Currency.BGN;
import static bg.fibank.cashdesk.model.Currency.EUR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataInitializer}.
 *
 * <p>Uses a real {@link BalanceFileRepository} backed by a {@code @TempDir}
 * rather than mocks, so we verify the full initialisation path end-to-end
 * without starting a Spring context.</p>
 */
class DataInitializerTest {

    @TempDir
    Path tempDir;

    private BalanceFileRepository repo;
    private DataInitializer initializer;

    @BeforeEach
    void setUp() throws IOException {
        AppProperties props = new AppProperties();
        props.getData().setBalancesFile(tempDir.resolve("cash_balances.txt").toString());
        props.getData().setTransactionsFile(tempDir.resolve("transactions.txt").toString());
        props.getAuth().setApiKey("test-key");

        repo = new BalanceFileRepository(props);
        repo.init();

        initializer = new DataInitializer(repo);
    }


    @Test
    @DisplayName("SEEDS list contains exactly three cashiers in spec order")
    void seeds_threeEntries_correctNames() {
        assertThat(DataInitializer.SEEDS).hasSize(3);
        assertThat(DataInitializer.SEEDS.get(0).name()).isEqualTo("MARTINA");
        assertThat(DataInitializer.SEEDS.get(1).name()).isEqualTo("PETER");
        assertThat(DataInitializer.SEEDS.get(2).name()).isEqualTo("LINDA");
    }

    @Test
    @DisplayName("STARTING_BGN totals exactly 1000 BGN per spec")
    void startingBgn_totals1000() {
        int total = DataInitializer.STARTING_BGN.stream()
                .mapToInt(d -> d.getFaceValue() * d.getCount())
                .sum();
        assertThat(total).isEqualTo(1000);
    }

    @Test
    @DisplayName("STARTING_BGN denominations: 50 × 10-BGN and 10 × 50-BGN")
    void startingBgn_correctDenominations() {
        assertThat(DataInitializer.STARTING_BGN).hasSize(2);
        assertThat(denominationCount(DataInitializer.STARTING_BGN, 10)).isEqualTo(50);
        assertThat(denominationCount(DataInitializer.STARTING_BGN, 50)).isEqualTo(10);
    }

    @Test
    @DisplayName("STARTING_EUR totals exactly 2000 EUR per spec")
    void startingEur_totals2000() {
        int total = DataInitializer.STARTING_EUR.stream()
                .mapToInt(d -> d.getFaceValue() * d.getCount())
                .sum();
        assertThat(total).isEqualTo(2000);
    }

    @Test
    @DisplayName("STARTING_EUR denominations: 100 × 10-EUR and 20 × 50-EUR")
    void startingEur_correctDenominations() {
        assertThat(DataInitializer.STARTING_EUR).hasSize(2);
        assertThat(denominationCount(DataInitializer.STARTING_EUR, 10)).isEqualTo(100);
        assertThat(denominationCount(DataInitializer.STARTING_EUR, 50)).isEqualTo(20);
    }


    @Nested
    @DisplayName("First boot — empty repository")
    class FirstBoot {

        @Test
        @DisplayName("run() seeds all three cashiers into the repository")
        void run_seedsAllThreeCashiers() throws Exception {
            initializer.run(new DefaultApplicationArguments());

            assertThat(repo.findAll()).hasSize(3);
            assertThat(repo.exists("MARTINA")).isTrue();
            assertThat(repo.exists("PETER")).isTrue();
            assertThat(repo.exists("LINDA")).isTrue();
        }

        @Test
        @DisplayName("MARTINA gets exactly 1000 BGN after seeding")
        void run_martina_bgn1000() throws Exception {
            initializer.run(new DefaultApplicationArguments());

            CashierBalance balance = repo.findByCashier("MARTINA").orElseThrow();
            assertThat(balance.getTotalForCurrency(BGN)).isEqualTo(1000);
        }

        @Test
        @DisplayName("MARTINA gets exactly 2000 EUR after seeding")
        void run_martina_eur2000() throws Exception {
            initializer.run(new DefaultApplicationArguments());

            CashierBalance balance = repo.findByCashier("MARTINA").orElseThrow();
            assertThat(balance.getTotalForCurrency(EUR)).isEqualTo(2000);
        }

        @Test
        @DisplayName("All three cashiers get identical starting amounts")
        void run_allCashiers_identicalStartingAmounts() throws Exception {
            initializer.run(new DefaultApplicationArguments());

            for (String name : new String[]{"MARTINA", "PETER", "LINDA"}) {
                CashierBalance balance = repo.findByCashier(name).orElseThrow();
                assertThat(balance.getTotalForCurrency(BGN))
                        .as("%s BGN", name).isEqualTo(1000);
                assertThat(balance.getTotalForCurrency(EUR))
                        .as("%s EUR", name).isEqualTo(2000);
            }
        }

        @Test
        @DisplayName("Seeded BGN denominations match spec: 50×10 and 10×50")
        void run_bgn_denominationsMatchSpec() throws Exception {
            initializer.run(new DefaultApplicationArguments());

            CashierBalance balance = repo.findByCashier("MARTINA").orElseThrow();
            var denoms = balance.getDenominationsForCurrency(BGN);

            assertThat(denoms).hasSize(2);
            assertThat(denoms.get(0).getFaceValue()).isEqualTo(10);
            assertThat(denoms.get(0).getCount()).isEqualTo(50);
            assertThat(denoms.get(1).getFaceValue()).isEqualTo(50);
            assertThat(denoms.get(1).getCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("Seeded EUR denominations match spec: 100×10 and 20×50")
        void run_eur_denominationsMatchSpec() throws Exception {
            initializer.run(new DefaultApplicationArguments());

            CashierBalance balance = repo.findByCashier("MARTINA").orElseThrow();
            var denoms = balance.getDenominationsForCurrency(EUR);

            assertThat(denoms).hasSize(2);
            assertThat(denoms.get(0).getFaceValue()).isEqualTo(10);
            assertThat(denoms.get(0).getCount()).isEqualTo(100);
            assertThat(denoms.get(1).getFaceValue()).isEqualTo(50);
            assertThat(denoms.get(1).getCount()).isEqualTo(20);
        }
    }


    @Nested
    @DisplayName("Subsequent boots — idempotency")
    class Idempotency {

        @Test
        @DisplayName("run() called twice does not create duplicate cashiers")
        void run_twice_noDuplicates() throws Exception {
            initializer.run(new DefaultApplicationArguments());
            initializer.run(new DefaultApplicationArguments());

            assertThat(repo.findAll()).hasSize(3);
        }

        @Test
        @DisplayName("run() does not overwrite a cashier modified after first seed")
        void run_doesNotOverwriteExistingBalance() throws Exception {
            initializer.run(new DefaultApplicationArguments());

            CashierBalance martina = repo.findByCashier("MARTINA").orElseThrow();
            martina.addDenominations(BGN, java.util.List.of(new Denomination(50, 10)));
            repo.save(martina);

            initializer.run(new DefaultApplicationArguments());

            CashierBalance reloaded = repo.findByCashier("MARTINA").orElseThrow();
            assertThat(reloaded.getTotalForCurrency(BGN))
                    .as("Balance must not be reset to 1000 after a second run()")
                    .isEqualTo(1500);
        }

        @Test
        @DisplayName("run() seeds only missing cashiers when one already exists")
        void run_partialSeed_onlyMissingCashiersSeeded() throws Exception {
            // Pre-seed MARTINA manually
            CashierBalance martina = new CashierBalance("MARTINA");
            martina.addDenominations(BGN,
                    java.util.List.of(new Denomination(10, 50), new Denomination(50, 10)));
            martina.addDenominations(EUR,
                    java.util.List.of(new Denomination(10, 100), new Denomination(50, 20)));
            repo.saveInitial(martina);

            initializer.run(new DefaultApplicationArguments());

            assertThat(repo.exists("MARTINA")).isTrue();
            assertThat(repo.exists("PETER")).isTrue();
            assertThat(repo.exists("LINDA")).isTrue();
        }
    }


    @Nested
    @DisplayName("Persistence after seed")
    class Persistence {

        @Test
        @DisplayName("Seeded data survives a repository restart (full file round-trip)")
        void seededData_survivesRestart() throws Exception {
            initializer.run(new DefaultApplicationArguments());

            AppProperties props = new AppProperties();
            props.getData().setBalancesFile(tempDir.resolve("cash_balances.txt").toString());
            props.getData().setTransactionsFile(tempDir.resolve("transactions.txt").toString());
            props.getAuth().setApiKey("test-key");

            BalanceFileRepository freshRepo = new BalanceFileRepository(props);
            freshRepo.init();

            assertThat(freshRepo.findByCashier("MARTINA").get().getTotalForCurrency(BGN))
                    .isEqualTo(1000);
            assertThat(freshRepo.findByCashier("PETER").get().getTotalForCurrency(EUR))
                    .isEqualTo(2000);
            assertThat(freshRepo.findByCashier("LINDA").get().getTotalForCurrency(BGN))
                    .isEqualTo(1000);
        }
    }


    private int denominationCount(java.util.List<Denomination> denoms, int faceValue) {
        return denoms.stream()
                .filter(d -> d.getFaceValue() == faceValue)
                .mapToInt(Denomination::getCount)
                .findFirst()
                .orElse(0);
    }
}
