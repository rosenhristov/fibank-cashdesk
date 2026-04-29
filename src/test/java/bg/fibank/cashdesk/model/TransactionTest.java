package bg.fibank.cashdesk.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static bg.fibank.cashdesk.model.Currency.BGN;
import static bg.fibank.cashdesk.model.Currency.EUR;
import static bg.fibank.cashdesk.model.OperationType.DEPOSIT;
import static bg.fibank.cashdesk.model.OperationType.WITHDRAWAL;
import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    private static final LocalDateTime FIXED_TIME =
            LocalDateTime.of(2025, 4, 29, 10, 0, 0);

    @Test
    @DisplayName("canonical constructor sets all record components correctly")
    void constructor_allFields() {
        var denominations = List.of(new Denomination(10, 10), new Denomination(50, 10));

        Transaction tx = new Transaction("MARTINA", DEPOSIT, BGN, 600, denominations, FIXED_TIME);

        assertThat(tx.timestamp()).isEqualTo(FIXED_TIME);
        assertThat(tx.cashierName()).isEqualTo("MARTINA");
        assertThat(tx.operationType()).isEqualTo(DEPOSIT);
        assertThat(tx.currency()).isEqualTo(BGN);
        assertThat(tx.amount()).isEqualTo(600);
        assertThat(tx.denominations()).hasSize(2);
    }

    @Test
    @DisplayName("static factory Transaction.of() produces the same result as canonical constructor")
    void staticFactory_equivalentToConstructor() {
        var denoms = List.of(new Denomination(50, 10));

        Transaction viaConstructor = new Transaction(
                "PETER", WITHDRAWAL, EUR, 500, denoms, FIXED_TIME);
        Transaction viaFactory = Transaction.of(
                "PETER", WITHDRAWAL, EUR, 500, denoms, FIXED_TIME);

        assertThat(viaFactory).isEqualTo(viaConstructor);
    }

    @Test
    @DisplayName("compact constructor wraps denominations as unmodifiable — external mutation is blocked")
    void compactConstructor_defensiveCopy() {
        var mutableList = new java.util.ArrayList<>(List.of(new Denomination(10, 10)));

        Transaction tx = new Transaction(
                "LINDA", DEPOSIT, BGN, 100, mutableList, FIXED_TIME);

        // mutating the original list after construction must not affect the record
        mutableList.add(new Denomination(50, 5));

        assertThat(tx.denominations()).hasSize(1);
    }

    @Test
    @DisplayName("null denominations are normalised to an empty list by the compact constructor")
    void compactConstructor_nullDenominations_becomesEmptyList() {
        Transaction tx = new Transaction(
                "PETER", WITHDRAWAL, EUR, 0, null, FIXED_TIME);

        assertThat(tx.denominations()).isEmpty();
    }

    // ── denominationsAsString ─────────────────────────────────────────────────

    @Test
    @DisplayName("denominationsAsString() serialises to compact pipe-file format")
    void denominationsAsString_deposit_bgn() {
        Transaction tx = Transaction.of("MARTINA", DEPOSIT, BGN, 600,
                                            List.of(new Denomination(10, 10), new Denomination(50, 10)),
                                        FIXED_TIME);

        assertThat(tx.denominationsAsString()).isEqualTo("10x10,50x10");
    }

    @Test
    @DisplayName("denominationsAsString() returns empty string for empty denominations")
    void denominationsAsString_empty_returnsEmpty() {
        Transaction tx = Transaction.of("PETER", WITHDRAWAL,
                EUR, 0, List.of(), FIXED_TIME);

        assertThat(tx.denominationsAsString()).isEmpty();
    }

    // ── parseDenominations ────────────────────────────────────────────────────

    @Test
    @DisplayName("parseDenominations() round-trips through denominationsAsString()")
    void parseDenominations_roundTrip() {
        var original = List.of(new Denomination(10, 10), new Denomination(50, 10));

        Transaction tx = Transaction.of( "LINDA",
                DEPOSIT, BGN, 600, original, FIXED_TIME);

        List<Denomination> parsed = Transaction.parseDenominations(tx.denominationsAsString());

        assertThat(parsed).hasSize(original.size());
        for (int i = 0; i < original.size(); i++) {
            assertThat(parsed.get(i).getFaceValue()).isEqualTo(original.get(i).getFaceValue());
            assertThat(parsed.get(i).getCount()).isEqualTo(original.get(i).getCount());
        }
    }

    @Test
    @DisplayName("parseDenominations() handles blank/null input gracefully")
    void parseDenominations_blankInput_returnsEmptyList() {
        assertThat(Transaction.parseDenominations(null)).isEmpty();
        assertThat(Transaction.parseDenominations("")).isEmpty();
        assertThat(Transaction.parseDenominations("   ")).isEmpty();
    }

    @Test
    @DisplayName("parseDenominations() handles withdrawal spec: 10×50 EUR")
    void parseDenominations_withdrawal_eur() {
        List<Denomination> result = Transaction.parseDenominations("50x10");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFaceValue()).isEqualTo(50);
        assertThat(result.get(0).getCount()).isEqualTo(10);
    }
}
