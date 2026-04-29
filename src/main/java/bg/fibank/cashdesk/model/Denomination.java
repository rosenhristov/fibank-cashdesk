package bg.fibank.cashdesk.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single denomination slot in a cashier's till — a combination
 * of a bill face value and how many of those bills are currently held.
 *
 * <p>Examples from the spec:</p>
 * <ul>
 *   <li>{@code Denomination(10, 50)}  → fifty 10-BGN notes  = 500 BGN</li>
 *   <li>{@code Denomination(50, 10)}  → ten  50-BGN notes   = 500 BGN</li>
 *   <li>{@code Denomination(10, 100)} → one hundred 10-EUR notes = 1000 EUR</li>
 * </ul>
 *
 * <p><strong>Mutability contract:</strong> {@code Denomination} is mutable by
 * design because {@link CashierBalance} adjusts counts in-place during deposits
 * and withdrawals. Callers that need an immutable snapshot (e.g. for
 * {@link Transaction}) must copy via {@link #copy()} before storing.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Denomination {

    /**
     * The face value of a single bill (e.g. 10, 20, 50, 100).
     */
    private int faceValue;

    /**
     * How many bills of this face value are currently in the till.
     */
    private int count;

    /**
     * Returns the monetary value represented by this denomination slot.
     *
     * @return {@code faceValue × count}
     */
    public int total() {
        return faceValue * count;
    }

    /**
     * Returns a new {@code Denomination} with the same values, decoupled from
     * this instance. Use this when snapshotting a denomination for a
     * {@link Transaction} record.
     */
    public Denomination copy() {
        return new Denomination(this.faceValue, this.count);
    }

    /**
     * Returns a compact representation suitable for file serialisation and logs,
     * e.g. {@code "10x50"} meaning fifty 10-unit bills.
     */
    @Override
    public String toString() {
        return String.format("%sx%s", faceValue, count);
    }

}
