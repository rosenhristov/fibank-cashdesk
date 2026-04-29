package bg.fibank.cashdesk.model;

import bg.fibank.cashdesk.exception.DenominationNotFoundException;
import bg.fibank.cashdesk.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashierBalanceTest {

    /**
     * Seed state matching the spec starting balance:
     *   BGN → 50×10, 10×50  = 1000 BGN
     *   EUR → 100×10, 20×50 = 2000 EUR
     */
    private CashierBalance balance;

    @BeforeEach
    void setUp() {
        balance = new CashierBalance("MARTINA");
        balance.addDenominations(Currency.BGN, List.of(new Denomination(10, 50), new Denomination(50, 10)));
        balance.addDenominations(Currency.EUR, List.of(new Denomination(10, 100), new Denomination(50, 20)));
    }

    // ── totalFor ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("totalFor()")
    class TotalFor {

        @Test
        @DisplayName("returns correct starting BGN total (1000)")
        void bgn_startingTotal() {
            assertThat(balance.getTotalForCurrency(Currency.BGN)).isEqualTo(1000);
        }

        @Test
        @DisplayName("returns correct starting EUR total (2000)")
        void eur_startingTotal() {
            assertThat(balance.getTotalForCurrency(Currency.EUR)).isEqualTo(2000);
        }

        @Test
        @DisplayName("returns 0 for a currency with no data")
        void unknownCurrency_returnsZero() {
            CashierBalance empty = new CashierBalance("PETER");
            assertThat(empty.getTotalForCurrency(Currency.BGN)).isEqualTo(0);
        }
    }

    // ── addDenominations ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("addDenominations()")
    class AddDenominations {

        @Test
        @DisplayName("deposit 600 BGN (10×10, 10×50) raises total to 1600")
        void deposit_bgn_600() {
            balance.addDenominations(Currency.BGN,
                    List.of(new Denomination(10, 10), new Denomination(50, 10)));

            assertThat(balance.getTotalForCurrency(Currency.BGN)).isEqualTo(1600);
        }

        @Test
        @DisplayName("deposit 200 EUR (5×20, 2×50) raises total to 2200")
        void deposit_eur_200() {
            balance.addDenominations(Currency.EUR,
                    List.of(new Denomination(20, 5), new Denomination(50, 2)));

            assertThat(balance.getTotalForCurrency(Currency.EUR)).isEqualTo(2200);
        }

        @Test
        @DisplayName("adds a new denomination slot when face value is not yet present")
        void deposit_newFaceValue_createsSlot() {
            // 20 BGN note does not exist in seed
            balance.addDenominations(Currency.BGN, List.of(new Denomination(20, 3)));

            assertThat(balance.getDenominationsForCurrency(Currency.BGN))
                    .extracting(Denomination::getFaceValue)
                    .contains(20);
        }

        @Test
        @DisplayName("increments existing slot rather than duplicating it")
        void deposit_existingFaceValue_incrementsCount() {
            int before = balance.getDenominationsForCurrency(Currency.BGN).stream()
                    .filter(d -> d.getFaceValue() == 10)
                    .mapToInt(Denomination::getCount).sum();

            balance.addDenominations(Currency.BGN, List.of(new Denomination(10, 5)));

            int after = balance.getDenominationsForCurrency(Currency.BGN).stream()
                    .filter(d -> d.getFaceValue() == 10)
                    .mapToInt(Denomination::getCount).sum();

            assertThat(after).isEqualTo(before + 5);
        }
    }

    // ── subtractDenominations ─────────────────────────────────────────────────

    @Nested
    @DisplayName("subtractDenominations()")
    class SubtractDenominations {

        @Test
        @DisplayName("withdrawal 100 BGN (5×10, 1×50) reduces total to 900")
        void withdraw_bgn_100() {
            balance.subtractDenominations(Currency.BGN,
                    List.of(new Denomination(10, 5), new Denomination(50, 1)));

            assertThat(balance.getTotalForCurrency(Currency.BGN)).isEqualTo(900);
        }

        @Test
        @DisplayName("withdrawal 500 EUR (10×50) reduces total to 1500")
        void withdraw_eur_500() {
            balance.subtractDenominations(Currency.EUR,
                    List.of(new Denomination(50, 10)));

            assertThat(balance.getTotalForCurrency(Currency.EUR)).isEqualTo(1500);
        }

        @Test
        @DisplayName("throws DenominationNotFoundException for unknown face value")
        void withdraw_unknownFaceValue_throws() {
            assertThatThrownBy(() ->
                    balance.subtractDenominations(Currency.BGN, List.of(new Denomination(200, 1)))
            )
                    .isInstanceOf(DenominationNotFoundException.class)
                    .hasMessageContaining("200")
                    .hasMessageContaining("MARTINA");
        }

        @Test
        @DisplayName("throws InsufficientFundsException when count is too low")
        void withdraw_insufficientCount_throws() {
            // only 50 × 10-BGN notes available; requesting 99
            assertThatThrownBy(() ->
                    balance.subtractDenominations(Currency.BGN, List.of(new Denomination(10, 99)))
            )
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("99")
                    .hasMessageContaining("50");
        }

        @Test
        @DisplayName("atomicity — balance is unchanged when ANY denomination fails validation")
        void withdraw_atomicity_noPartialMutation() {
            int bgnBefore = balance.getTotalForCurrency(Currency.BGN);

            // first denomination (10×1) is valid; second (200×1) is not
            assertThatThrownBy(() ->
                    balance.subtractDenominations(Currency.BGN,
                            List.of(new Denomination(10, 1), new Denomination(200, 1)))
            ).isInstanceOf(DenominationNotFoundException.class);

            // total must be exactly as before — the valid part was not applied
            assertThat(balance.getTotalForCurrency(Currency.BGN)).isEqualTo(bgnBefore);
        }
    }


    @Nested
    @DisplayName("denominationsFor() defensive copy")
    class DenominationsForDefensiveCopy {

        @Test
        @DisplayName("mutating the returned list does not affect live balance")
        void returnedList_isDefensiveCopy() {
            List<Denomination> snapshot = balance.getDenominationsForCurrency(Currency.BGN);
            snapshot.get(0).setCount(9999);

            // live balance must be unchanged
            assertThat(balance.getTotalForCurrency(Currency.BGN)).isEqualTo(1000);
        }

        @Test
        @DisplayName("returned list is sorted ascending by face value")
        void returnedList_sortedAscending() {
            List<Integer> faceValues = balance.getDenominationsForCurrency(Currency.BGN)
                    .stream().map(Denomination::getFaceValue).toList();

            assertThat(faceValues).isSorted();
        }
    }
}
