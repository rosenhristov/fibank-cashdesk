package bg.fibank.cashdesk.init;

import bg.fibank.cashdesk.model.Denomination;

import java.util.List;

/**
 * Immutable description of a cashier's starting balance used exclusively by
 * {@link DataInitializer} to seed the system on first boot.
 *
 * <p>Kept as a separate record so the initializer's {@code SEEDS} list is
 * declarative and readable — each entry is one line of data, not a block of
 * imperative setup code.</p>
 *
 * @param name            cashier name (stored and compared upper-cased)
 * @param bgnDenominations starting BGN denomination breakdown
 * @param eurDenominations starting EUR denomination breakdown
 */
public record CashierSeed(String name,
                          List<Denomination> bgnDenominations,
                          List<Denomination> eurDenominations) {
    /** Returns the total BGN starting amount for this seed. */
    public int totalBgn() {
        return bgnDenominations.stream()
                .mapToInt(d -> d.getFaceValue() * d.getCount())
                .sum();
    }

    /** Returns the total EUR starting amount for this seed. */
    public int totalEur() {
        return eurDenominations.stream()
                .mapToInt(d -> d.getFaceValue() * d.getCount())
                .sum();
    }

    /**
     * Convenience factory — avoids the need to write {@code List.of} at
     * every call site in the seeds list.
     */
    public static CashierSeed of(String name, List<Denomination> bgn, List<Denomination> eur) {
        return new CashierSeed(name, List.copyOf(bgn), List.copyOf(eur));
    }

    /** Expresses the seed as a log-friendly summary string. */
    @Override
    public String toString() {
        return "%s [BGN=%d, EUR=%d]".formatted(name, totalBgn(), totalEur());
    }
}