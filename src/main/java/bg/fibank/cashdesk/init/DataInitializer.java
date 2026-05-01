package bg.fibank.cashdesk.init;

import bg.fibank.cashdesk.model.CashierBalance;
import bg.fibank.cashdesk.model.Denomination;
import bg.fibank.cashdesk.repository.BalanceFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static bg.fibank.cashdesk.model.Currency.BGN;
import static bg.fibank.cashdesk.model.Currency.EUR;

/**
 * Seeds the three required cashiers (MARTINA, PETER, LINDA) with their
 * specification-mandated starting balances on first application boot.
 *
 * <h2>Idempotency</h2>
 * <p>Before seeding, each cashier is checked for existence in the
 * {@link BalanceFileRepository}. If a cashier is already present (because the
 * balance file was preserved from a previous run) their entry is left
 * completely untouched. This makes the initializer safe to call on every boot
 * without ever overwriting live operational data.</p>
 *
 * <h2>Ordering</h2>
 * <p>{@link ApplicationRunner} runs after the full Spring context is ready and
 * after all {@code @PostConstruct} methods — including
 * {@code BalanceFileRepository.init()} — have completed. This guarantees the
 * repository's in-memory cache and file path are both initialised before the
 * seeding loop begins.</p>
 *
 * <h2>Spec starting balances</h2>
 * <pre>
 *   BGN: 1 000 BGN  →  50 × 10 BGN  +  10 × 50 BGN
 *   EUR: 2 000 EUR  → 100 × 10 EUR  +  20 × 50 EUR
 * </pre>
 * <p>All three cashiers receive identical starting amounts per the specification.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    // ── spec-mandated starting denominations (shared by all three cashiers) ──

    /**
     * Starting BGN denominations: 50 × 10-BGN + 10 × 50-BGN = 1 000 BGN.
     */
    static final List<Denomination> STARTING_BGN = List.of(
            new Denomination(10, 50),   // 50 × 10 BGN = 500 BGN
            new Denomination(50, 10)    // 10 × 50 BGN = 500 BGN
    );

    /**
     * Starting EUR denominations: 100 × 10-EUR + 20 × 50-EUR = 2 000 EUR.
     */
    static final List<Denomination> STARTING_EUR = List.of(
            new Denomination(10, 100),  // 100 × 10 EUR = 1 000 EUR
            new Denomination(50, 20)    // 20  × 50 EUR = 1 000 EUR
    );

    /**
     * Ordered list of cashiers to seed. Insertion order determines the order
     * in which cashiers appear in the balance file and API responses.
     */
    static final List<CashierSeed> SEEDS = List.of(
            CashierSeed.of("MARTINA", STARTING_BGN, STARTING_EUR),
            CashierSeed.of("PETER",   STARTING_BGN, STARTING_EUR),
            CashierSeed.of("LINDA",   STARTING_BGN, STARTING_EUR));

    private final BalanceFileRepository balanceFileRepository;


    @Override
    public void run(ApplicationArguments args) throws IOException {
        log.info("INIT | Starting cashier seed check for {} cashier(s)", SEEDS.size());

        int seeded  = 0;
        int skipped = 0;

        for (CashierSeed seed : SEEDS) {
            if (balanceFileRepository.exists(seed.name())) {
                log.info("INIT | SKIP  | {} — already initialised", seed.name());
                skipped++;
            } else {
                seedCashier(seed);
                seeded++;
            }
        }

        logSummary(seeded, skipped);
    }

    /**
     * Builds a {@link CashierBalance} from the seed definition and persists it
     * via {@link BalanceFileRepository#saveInitial}.
     */
    private void seedCashier(CashierSeed seed) throws IOException {
        CashierBalance balance = new CashierBalance(seed.name());
        balance.addDenominations(BGN, seed.bgnDenominations());
        balance.addDenominations(EUR, seed.eurDenominations());

        balanceFileRepository.saveInitial(balance);

        log.info("INIT | SEEDED | {} | BGN={} ({} denominations) | EUR={} ({} denominations)",
                seed.name(),
                seed.totalBgn(), seed.bgnDenominations().size(),
                seed.totalEur(), seed.eurDenominations().size());
    }

    /**
     * Logs a human-readable startup summary showing the resulting balance state
     * for every cashier, regardless of whether they were freshly seeded or
     * already existed.
     */
    private void logSummary(int seeded, int skipped) {
        log.info("INIT | Complete | seeded={} skipped={}", seeded, skipped);
        log.info("INIT | ── Cashier starting balances ──────────────────────────");

        for (CashierSeed seed : SEEDS) {
            balanceFileRepository.findByCashier(seed.name()).ifPresent(balance ->
                    log.info("INIT | │  {}  BGN={}  EUR={}",
                            balance.getCashierName(),
                            balance.getTotalForCurrency(BGN),
                            balance.getTotalForCurrency(EUR))
            );
        }

        log.info("INIT | ────────────────────────────────────────────────────────");
    }
}
