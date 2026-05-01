package bg.fibank.cashdesk.service;

import bg.fibank.cashdesk.config.AppProperties;
import bg.fibank.cashdesk.dto.*;
import bg.fibank.cashdesk.exception.CashierNotFoundException;
import bg.fibank.cashdesk.exception.InsufficientFundsException;
import bg.fibank.cashdesk.exception.InvalidOperationException;
import bg.fibank.cashdesk.init.DataInitializer;
import bg.fibank.cashdesk.model.Currency;
import bg.fibank.cashdesk.model.OperationType;
import bg.fibank.cashdesk.repository.BalanceFileRepository;
import bg.fibank.cashdesk.repository.TransactionFileRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration-style unit tests for {@link CashDeskService}.
 *
 * <p>Uses real repositories backed by a {@code @TempDir}, seeded via
 * {@link DataInitializer}, so every test starts from the spec starting state:
 * 1 000 BGN and 2 000 EUR per cashier.</p>
 */
class CashDeskServiceTest {

    @TempDir
    Path tempDir;

    private CashDeskService service;
    private BalanceFileRepository balanceRepo;
    private TransactionFileRepository txRepo;

    @BeforeEach
    void setUp() throws IOException {
        AppProperties props = new AppProperties();
        props.getData().setBalancesFile(tempDir.resolve("cash_balances.txt").toString());
        props.getData().setTransactionsFile(tempDir.resolve("transactions.txt").toString());
        props.getAuth().setApiKey("test-key");

        balanceRepo = new BalanceFileRepository(props);
        balanceRepo.init();

        txRepo = new TransactionFileRepository(props);
        txRepo.init();

        // Seed all three cashiers to spec starting state
        DataInitializer initializer = new DataInitializer(balanceRepo);
        initializer.run(new DefaultApplicationArguments());

        service = new CashDeskService(balanceRepo, txRepo);
    }

    // ── deposit ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deposit operations")
    class Deposits {

        @Test
        @DisplayName("deposit 600 BGN (10×10 + 50×10) increases MARTINA's BGN to 1600")
        void deposit_bgn600_balanceIncreases() {
            CashOperationResponse response = service.performOperation(depositBgn600("MARTINA"));

            assertThat(response.cashierName()).isEqualTo("MARTINA");
            assertThat(response.operationType()).isEqualTo(OperationType.DEPOSIT);
            assertThat(response.currency()).isEqualTo(Currency.BGN);
            assertThat(response.newBalance()).isEqualTo(1600);
        }

        @Test
        @DisplayName("deposit 200 EUR (20×5 + 50×2) increases MARTINA's EUR to 2200")
        void deposit_eur200_balanceIncreases() {
            CashOperationResponse response = service.performOperation(depositEur200("MARTINA"));

            assertThat(response.newBalance()).isEqualTo(2200);
            assertThat(response.currency()).isEqualTo(Currency.EUR);
        }

        @Test
        @DisplayName("deposit BGN updates denomination counts correctly")
        void deposit_bgn_denominationCountsCorrect() {
            service.performOperation(depositBgn600("MARTINA"));

            CashBalanceResponse balances = service.getBalances("MARTINA", null, null);
            CashierBalanceDto cashier = balances.cashiers().get(0);

            // 10-BGN: was 50, added 10 → 60
            assertTenDenominationCount(cashier.bgn().denominations(), 10, 60);
            // 50-BGN: was 10, added 10 → 20
            assertTenDenominationCount(cashier.bgn().denominations(), 50, 20);
        }

        @Test
        @DisplayName("deposit EUR adds new 20-EUR denomination slot")
        void deposit_eur_newDenominationSlotAdded() {
            service.performOperation(depositEur200("MARTINA"));

            CashBalanceResponse balances = service.getBalances("MARTINA", null, null);
            var eurDenoms = balances.cashiers().get(0).eur().denominations();

            // 20-EUR slot must now exist (not in starting state)
            boolean has20 = eurDenoms.stream().anyMatch(d -> d.faceValue() == 20);
            assertThat(has20).isTrue();
        }

        @Test
        @DisplayName("deposit does not affect the other cashiers' balances")
        void deposit_doesNotAffectOtherCashiers() {
            service.performOperation(depositBgn600("MARTINA"));

            assertThat(balanceRepo.findByCashier("PETER").get().getTotalForCurrency(Currency.BGN))
                    .isEqualTo(1000);
            assertThat(balanceRepo.findByCashier("LINDA").get().getTotalForCurrency(Currency.BGN))
                    .isEqualTo(1000);
        }

        @Test
        @DisplayName("deposit appends one transaction record")
        void deposit_appendsTransaction() {
            service.performOperation(depositBgn600("MARTINA"));

            assertThat(txRepo.findAll("MARTINA", null, null)).hasSize(1);
        }
    }

    // ── withdrawal ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Withdrawal operations")
    class Withdrawals {

        @Test
        @DisplayName("withdraw 100 BGN (10×5 + 50×1) decreases MARTINA's BGN to 900")
        void withdraw_bgn100_balanceDecreases() {
            CashOperationResponse response = service.performOperation(withdrawBgn100("MARTINA"));

            assertThat(response.newBalance()).isEqualTo(900);
            assertThat(response.currency()).isEqualTo(Currency.BGN);
        }

        @Test
        @DisplayName("withdraw 500 EUR (50×10) decreases MARTINA's EUR to 1500")
        void withdraw_eur500_balanceDecreases() {
            CashOperationResponse response = service.performOperation(withdrawEur500("MARTINA"));

            assertThat(response.newBalance()).isEqualTo(1500);
            assertThat(response.currency()).isEqualTo(Currency.EUR);
        }

        @Test
        @DisplayName("withdraw BGN updates denomination counts correctly")
        void withdraw_bgn_denominationCountsCorrect() {
            service.performOperation(withdrawBgn100("MARTINA"));

            CashBalanceResponse balances = service.getBalances("MARTINA", null, null);
            var bgnDenoms = balances.cashiers().get(0).bgn().denominations();

            // 10-BGN: was 50, removed 5 → 45
            assertTenDenominationCount(bgnDenoms, 10, 45);
            // 50-BGN: was 10, removed 1 → 9
            assertTenDenominationCount(bgnDenoms, 50, 9);
        }

        @Test
        @DisplayName("withdrawal appends one transaction record")
        void withdraw_appendsTransaction() {
            service.performOperation(withdrawBgn100("MARTINA"));

            assertThat(txRepo.findAll("MARTINA", null, null)).hasSize(1);
        }

        @Test
        @DisplayName("throws InsufficientFundsException when count is too low")
        void withdraw_insufficientCount_throws() {
            // only 20 × 50-EUR available, requesting 21
            CashOperationRequest req = new CashOperationRequest(
                    "MARTINA", OperationType.WITHDRAWAL, Currency.EUR,
                    1050, List.of(new DenominationDto(50, 21)));

            assertThatThrownBy(() -> service.performOperation(req))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        @DisplayName("failed withdrawal leaves balance completely unchanged")
        void withdraw_failure_balanceUnchanged() {
            CashOperationRequest req = new CashOperationRequest(
                    "MARTINA", OperationType.WITHDRAWAL, Currency.EUR,
                    1050, List.of(new DenominationDto(50, 21)));

            assertThatThrownBy(() -> service.performOperation(req))
                    .isInstanceOf(InsufficientFundsException.class);

            assertThat(balanceRepo.findByCashier("MARTINA").get().getTotalForCurrency(Currency.EUR))
                    .isEqualTo(2000);
        }
    }

    // ── full spec scenario ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Full spec scenario (all four operations on MARTINA)")
    class FullSpecScenario {

        @Test
        @DisplayName("deposit BGN + deposit EUR + withdraw BGN + withdraw EUR → correct final balances")
        void fullSpecScenario_finalBalancesCorrect() {
            service.performOperation(depositBgn600("MARTINA"));
            service.performOperation(depositEur200("MARTINA"));
            service.performOperation(withdrawBgn100("MARTINA"));
            service.performOperation(withdrawEur500("MARTINA"));

            var balance = balanceRepo.findByCashier("MARTINA").orElseThrow();
            assertThat(balance.getTotalForCurrency(Currency.BGN)).isEqualTo(1500); // 1000+600-100
            assertThat(balance.getTotalForCurrency(Currency.EUR)).isEqualTo(1700); // 2000+200-500
        }

        @Test
        @DisplayName("full spec scenario records exactly four transactions for MARTINA")
        void fullSpecScenario_fourTransactionsRecorded() {
            service.performOperation(depositBgn600("MARTINA"));
            service.performOperation(depositEur200("MARTINA"));
            service.performOperation(withdrawBgn100("MARTINA"));
            service.performOperation(withdrawEur500("MARTINA"));

            assertThat(txRepo.findAll("MARTINA", null, null)).hasSize(4);
        }

        @Test
        @DisplayName("full spec scenario: PETER and LINDA balances remain at starting values")
        void fullSpecScenario_otherCashiersUnaffected() {
            service.performOperation(depositBgn600("MARTINA"));
            service.performOperation(depositEur200("MARTINA"));
            service.performOperation(withdrawBgn100("MARTINA"));
            service.performOperation(withdrawEur500("MARTINA"));

            for (String name : List.of("PETER", "LINDA")) {
                var b = balanceRepo.findByCashier(name).orElseThrow();
                assertThat(b.getTotalForCurrency(Currency.BGN)).as("%s BGN", name).isEqualTo(1000);
                assertThat(b.getTotalForCurrency(Currency.EUR)).as("%s EUR", name).isEqualTo(2000);
            }
        }
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("throws InvalidOperationException when denomination sum ≠ stated amount")
        void denominationSumMismatch_throws() {
            // denominations sum to 600 but stated amount is 500
            CashOperationRequest req = new CashOperationRequest(
                    "MARTINA", OperationType.DEPOSIT, Currency.BGN,
                    500, List.of(new DenominationDto(10, 10), new DenominationDto(50, 10)));

            assertThatThrownBy(() -> service.performOperation(req))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("600")
                    .hasMessageContaining("500");
        }

        @Test
        @DisplayName("throws CashierNotFoundException for an unknown cashier")
        void unknownCashier_throws() {
            CashOperationRequest req = new CashOperationRequest(
                    "NOBODY", OperationType.DEPOSIT, Currency.BGN,
                    100, List.of(new DenominationDto(10, 10)));

            assertThatThrownBy(() -> service.performOperation(req))
                    .isInstanceOf(CashierNotFoundException.class)
                    .hasMessageContaining("NOBODY");
        }

        @Test
        @DisplayName("cashier name is normalised to upper-case before lookup")
        void cashierName_normalisedToUpperCase() {
            CashOperationResponse response = service.performOperation(
                    new CashOperationRequest("martina", OperationType.DEPOSIT, Currency.BGN,
                            100, List.of(new DenominationDto(10, 10))));

            assertThat(response.cashierName()).isEqualTo("MARTINA");
        }
    }

    // ── getBalances ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getBalances()")
    class GetBalances {

        @Test
        @DisplayName("no filters → returns all three cashiers")
        void noFilters_returnsAllThree() {
            CashBalanceResponse response = service.getBalances(null, null, null);

            assertThat(response.cashiers()).hasSize(3);
        }

        @Test
        @DisplayName("cashier filter → returns only the specified cashier")
        void cashierFilter_returnsSingleCashier() {
            CashBalanceResponse response = service.getBalances("MARTINA", null, null);

            assertThat(response.cashiers()).hasSize(1);
            assertThat(response.cashiers().get(0).cashierName()).isEqualTo("MARTINA");
        }

        @Test
        @DisplayName("cashier filter is case-insensitive")
        void cashierFilter_caseInsensitive() {
            assertThat(service.getBalances("martina", null, null).cashiers()).hasSize(1);
        }

        @Test
        @DisplayName("unknown cashier in getBalances throws CashierNotFoundException")
        void unknownCashier_throws() {
            assertThatThrownBy(() -> service.getBalances("GHOST", null, null))
                    .isInstanceOf(CashierNotFoundException.class);
        }

        @Test
        @DisplayName("response echoes back the applied filter parameters")
        void response_echoesFilters() {
            LocalDate from = LocalDate.of(2025, 1, 1);
            LocalDate to   = LocalDate.of(2025, 12, 31);

            CashBalanceResponse response = service.getBalances("MARTINA", from, to);

            assertThat(response.cashier()).isEqualTo("MARTINA");
            assertThat(response.dateFrom()).isEqualTo(from);
            assertThat(response.dateTo()).isEqualTo(to);
        }

        @Test
        @DisplayName("starting BGN balance is 1000 for all three cashiers")
        void startingBgnBalance_1000_allCashiers() {
            CashBalanceResponse response = service.getBalances(null, null, null);

            response.cashiers().forEach(cashier ->
                    assertThat(cashier.bgn().total())
                            .as("%s BGN", cashier.cashierName())
                            .isEqualTo(1000));
        }

        @Test
        @DisplayName("starting EUR balance is 2000 for all three cashiers")
        void startingEurBalance_2000_allCashiers() {
            CashBalanceResponse response = service.getBalances(null, null, null);

            response.cashiers().forEach(cashier ->
                    assertThat(cashier.eur().total())
                            .as("%s EUR", cashier.cashierName())
                            .isEqualTo(2000));
        }
    }

    // ── spec request fixtures ─────────────────────────────────────────────────

    private CashOperationRequest depositBgn600(String cashier) {
        return new CashOperationRequest(cashier, OperationType.DEPOSIT, Currency.BGN,
                600, List.of(new DenominationDto(10, 10), new DenominationDto(50, 10)));
    }

    private CashOperationRequest depositEur200(String cashier) {
        return new CashOperationRequest(cashier, OperationType.DEPOSIT, Currency.EUR,
                200, List.of(new DenominationDto(20, 5), new DenominationDto(50, 2)));
    }

    private CashOperationRequest withdrawBgn100(String cashier) {
        return new CashOperationRequest(cashier, OperationType.WITHDRAWAL, Currency.BGN,
                100, List.of(new DenominationDto(10, 5), new DenominationDto(50, 1)));
    }

    private CashOperationRequest withdrawEur500(String cashier) {
        return new CashOperationRequest(cashier, OperationType.WITHDRAWAL, Currency.EUR,
                500, List.of(new DenominationDto(50, 10)));
    }

    // ── assertion helpers ─────────────────────────────────────────────────────

    private void assertTenDenominationCount(List<DenominationDto> denoms,
                                            int faceValue, int expectedCount) {
        int actual = denoms.stream()
                .filter(d -> d.faceValue() == faceValue)
                .mapToInt(DenominationDto::count)
                .findFirst()
                .orElse(-1);
        assertThat(actual)
                .as("count of %d-denomination bills", faceValue)
                .isEqualTo(expectedCount);
    }
}