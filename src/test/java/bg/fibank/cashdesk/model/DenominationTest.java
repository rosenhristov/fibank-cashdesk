package bg.fibank.cashdesk.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DenominationTest {

    @Test
    @DisplayName("total() returns faceValue × count")
    void total_returnsProduct() {
        assertThat(new Denomination(10, 50).total()).isEqualTo(500);
        assertThat(new Denomination(50, 10).total()).isEqualTo(500);
        assertThat(new Denomination(10, 100).total()).isEqualTo(1000);
        assertThat(new Denomination(20, 50).total()).isEqualTo(1000);
    }

    @Test
    @DisplayName("total() returns 0 when count is 0")
    void total_zeroCount_returnsZero() {
        assertThat(new Denomination(50, 0).total()).isEqualTo(0);
    }

    @Test
    @DisplayName("copy() produces an equal but independent object")
    void copy_isIndependent() {
        Denomination original = new Denomination(10, 50);
        Denomination copy     = original.copy();

        assertThat(copy).isEqualTo(original);

        // mutating the copy must not affect the original
        copy.setCount(99);
        assertThat(original.getCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("toString() uses faceValue×count compact format")
    void toString_compactFormat() {
        assertThat(new Denomination(10, 50).toString()).isEqualTo("10x50");
        assertThat(new Denomination(50, 10).toString()).isEqualTo("50x10");
    }
}
