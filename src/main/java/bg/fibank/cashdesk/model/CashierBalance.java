package bg.fibank.cashdesk.model;

import bg.fibank.cashdesk.exception.DenominationNotFoundException;
import bg.fibank.cashdesk.exception.InsufficientFundsException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Domain aggregate that owns the live balance state for a single cashier.
 *
 * <h2>Structure</h2>
 * <p>Balances are stored as {@code Map<Currency, List<Denomination>>}.
 * Each {@link Denomination} slot tracks the count of one bill face value
 * (e.g. fifty 10-BGN notes, ten 50-BGN notes).</p>
 *
 * <h2>Mutation</h2>
 * <ul>
 *   <li>{@link #addDenominations} — increments counts for matching face values,
 *       inserts a new slot if the face value is not yet present.</li>
 *   <li>{@link #subtractDenominations} — validates ALL requested denominations
 *       atomically before touching any count, then subtracts.  If any check
 *       fails the balance is left completely unchanged.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>This class is <em>not</em> thread-safe on its own.  Callers
 * ({@code CashDeskService}) must hold a per-cashier lock before mutating.</p>
 */
@Slf4j
@Getter
public class CashierBalance {

    private final String cashierName;

    /**
     * Live denomination state keyed by currency.
     * An {@link EnumMap} preserves the declaration order of {@link Currency}
     * (BGN before EUR), which is the order used in file and API output.
     */
    private final Map<Currency, List<Denomination>> denominations;


    /**
     * Creates an empty balance holder; use {@link #addDenominations} to seed it.
     */
    public CashierBalance(String cashierName) {
        this.cashierName    = Objects.requireNonNull(cashierName, "cashierName must not be null");
        this.denominations  = new EnumMap<>(Currency.class);
    }

    /**
     * Reconstructs a balance from a pre-built denomination map.
     * Used by {@code BalanceFileRepository} when loading from disk.
     */
    public CashierBalance(String cashierName, Map<Currency, List<Denomination>> denominations) {
        this.cashierName   = Objects.requireNonNull(cashierName, "cashierName must not be null");
        this.denominations = new EnumMap<>(denominations);
    }

    // ── query ────────────────────────────────────────────────────────────────

    /**
     * Returns the total monetary value held for a currency
     * (sum of {@code faceValue × count} across all denomination slots).
     *
     * @return 0 if no denomination data exists for the currency
     */
    public int getTotalForCurrency(Currency currency) {
        return denominations
                .getOrDefault(currency, Collections.emptyList())
                .stream()
                .mapToInt(Denomination::total)
                .sum();
    }

    /**
     * Returns the denomination list for a given currency, sorted ascending by
     * face value.  The list is a defensive copy — mutations do not affect the
     * live balance.
     */
    public List<Denomination> getDenominationsForCurrency(Currency currency) {
        return denominations
                .getOrDefault(currency, Collections.emptyList())
                .stream()
                .map(Denomination::copy)
                .sorted(Comparator.comparingInt(Denomination::getFaceValue))
                .toList();
    }


    /**
     * Adds the provided denominations to this cashier's balance.
     *
     * <p>For each incoming {@link Denomination}: if a slot with the same face
     * value already exists its count is incremented; otherwise a new slot is
     * appended.</p>
     *
     * @param currency             the currency of the deposit
     * @param denominationsToAdd   the denominations being deposited (must not be empty)
     */
    public void addDenominations(Currency currency, List<Denomination> denominationsToAdd) {
        validateNonNull(currency, denominationsToAdd);

        List<Denomination> current = denominations.computeIfAbsent(currency, k -> new ArrayList<>());

        for (Denomination incoming : denominationsToAdd) {
            Denomination existing = findByFaceValue(current, incoming.getFaceValue());
            if (nonNull(existing)) {
                existing.setCount(existing.getCount() + incoming.getCount());
            } else {
                current.add(new Denomination(incoming.getFaceValue(), incoming.getCount()));
            }
        }

        log.debug("BALANCE | ADD | cashier={} currency={} denominations={} newTotal={}",
                cashierName, currency, denominationsToAdd, getTotalForCurrency(currency));
    }

    /**
     * Subtracts the provided denominations from cashier's balance.
     *
     * <p><strong>Atomic validation:</strong> every requested denomination is
     * checked for existence and sufficiency before any count is decremented.
     * If any check fails the balance remains completely unchanged.</p>
     *
     * @param currency                       the currency of the withdrawal
     * @param denominationsToSubtract        the denominations being withdrawn
     * @throws DenominationNotFoundException if a requested face value has no slot
     * @throws InsufficientFundsException    if a slot exists but holds fewer bills
     *                                       than requested
     */
    public void subtractDenominations(Currency currency, List<Denomination> denominationsToSubtract) {
        validateNonNull(currency, denominationsToSubtract);

        List<Denomination> currentDenominations = denominations.getOrDefault(currency, Collections.emptyList());

        verifySubtractDenominationsExistence(currency, denominationsToSubtract, currentDenominations);

        applySubtractDenominations(denominationsToSubtract, currentDenominations);

        log.debug("BALANCE | SUBTRACT | cashier={} currency={} denominations={} newTotal={}",
                  cashierName, currency, denominationsToSubtract, getTotalForCurrency(currency));
    }


    private void validateNonNull(Currency currency, List<Denomination> denominations) {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(denominations, "denominations must not be null");
    }

    private void verifySubtractDenominationsExistence(Currency currency, List<Denomination> denominationsToSubtract,
                                                      List<Denomination> current) {
        for (Denomination requested : denominationsToSubtract) {
            Denomination existing = findByFaceValue(current, requested.getFaceValue());

            if (isNull(existing)) {
                throw new DenominationNotFoundException(cashierName, currency, requested.getFaceValue());
            }

            if (existing.getCount() < requested.getCount()) {
                throw new InsufficientFundsException(cashierName, currency, requested.getFaceValue(),
                                                     requested.getCount(), existing.getCount());
            }
        }
    }

    private void applySubtractDenominations(List<Denomination> denominationsToSubtract,
                                             List<Denomination> currDenominations) {
        for (Denomination requested : denominationsToSubtract) {
            Denomination existing = findByFaceValue(currDenominations, requested.getFaceValue());
            existing.setCount(existing.getCount() - requested.getCount());
        }
    }

    private Denomination findByFaceValue(List<Denomination> list, int faceValue) {
        return list.stream()
                .filter(d -> d.getFaceValue() == faceValue)
                .findFirst()
                .orElse(null);
    }
}
