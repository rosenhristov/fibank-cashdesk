package bg.fibank.cashdesk.repository;

import bg.fibank.cashdesk.exception.FileFormatException;
import bg.fibank.cashdesk.model.Denomination;
import bg.fibank.cashdesk.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;

import static bg.fibank.cashdesk.model.Currency.BGN;
import static bg.fibank.cashdesk.model.Currency.EUR;
import static bg.fibank.cashdesk.model.OperationType.DEPOSIT;
import static bg.fibank.cashdesk.model.OperationType.WITHDRAWAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileFormatTest {

    // ── shared fixtures ───────────────────────────────────────────────────────

    private static final LocalDateTime TS =
            LocalDateTime.of(2025, 4, 29, 10, 0, 0);

    // ── isSkippable ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isSkippable()")
    class IsSkippable {

        @ParameterizedTest(name = "[{index}] \"{0}\" → skippable")
        @ValueSource(strings = {"", "   ", "#comment", "# CASHIER|CURRENCY|FACE_VALUE|COUNT", "\t"})
        @DisplayName("blank lines and comment lines are skippable")
        void blankAndComment_areSkippable(String line) {
            assertThat(FileFormat.isSkippable(line)).isTrue();
        }

        @Test
        @DisplayName("null is skippable")
        void null_isSkippable() {
            assertThat(FileFormat.isSkippable(null)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → not skippable")
        @ValueSource(strings = {
                "MARTINA|BGN|10|50",
                "2025-04-29T10:00:00|MARTINA|DEPOSIT|BGN|600|10x10,10x50"
        })
        @DisplayName("data lines are not skippable")
        void dataLines_areNotSkippable(String line) {
            assertThat(FileFormat.isSkippable(line)).isFalse();
        }
    }

    // ── balance file codec ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Balance file codec")
    class BalanceCodec {

        @Test
        @DisplayName("encodeBalanceLine() produces CASHIER|CURRENCY|FACE_VALUE|COUNT format")
        void encode_producesCorrectFormat() {
            String line = FileFormat.encodeBalanceLine(
                    "MARTINA", BGN, new Denomination(10, 50));

            assertThat(line).isEqualTo("MARTINA|BGN|10|50");
        }

        @Test
        @DisplayName("encodeBalanceLine() works for EUR denominations")
        void encode_eurDenomination() {
            String line = FileFormat.encodeBalanceLine(
                    "PETER", EUR, new Denomination(50, 20));

            assertThat(line).isEqualTo("PETER|EUR|50|20");
        }

        @Test
        @DisplayName("decodeBalanceLine() parses all four columns correctly")
        void decode_allColumnsCorrect() {
            FileFormat.BalanceLine result =
                    FileFormat.decodeBalanceLine("MARTINA|BGN|10|50");

            assertThat(result.cashierName()).isEqualTo("MARTINA");
            assertThat(result.currency()).isEqualTo(BGN);
            assertThat(result.faceValue()).isEqualTo(10);
            assertThat(result.count()).isEqualTo(50);
        }

        @Test
        @DisplayName("encode → decode round-trip is lossless")
        void encode_decode_roundTrip() {
            Denomination original = new Denomination(50, 10);
            String encoded = FileFormat.encodeBalanceLine("LINDA", EUR, original);
            FileFormat.BalanceLine decoded = FileFormat.decodeBalanceLine(encoded);

            assertThat(decoded.cashierName()).isEqualTo("LINDA");
            assertThat(decoded.currency()).isEqualTo(EUR);
            assertThat(decoded.faceValue()).isEqualTo(original.getFaceValue());
            assertThat(decoded.count()).isEqualTo(original.getCount());
        }

        @Test
        @DisplayName("decodeBalanceLine() strips surrounding whitespace from each column")
        void decode_stripsWhitespace() {
            FileFormat.BalanceLine result =
                    FileFormat.decodeBalanceLine(" MARTINA | BGN | 10 | 50 ");

            assertThat(result.cashierName()).isEqualTo("MARTINA");
            assertThat(result.faceValue()).isEqualTo(10);
        }

        @Test
        @DisplayName("decodeBalanceLine() throws FileFormatException for too few columns")
        void decode_tooFewColumns_throws() {
            assertThatThrownBy(() -> FileFormat.decodeBalanceLine("MARTINA|BGN|10"))
                    .isInstanceOf(FileFormatException.class)
                    .hasMessageContaining("expected " + FileFormat.BAL_COL_COUNT_TOTAL);
        }

        @Test
        @DisplayName("decodeBalanceLine() throws FileFormatException for unknown currency")
        void decode_unknownCurrency_throws() {
            assertThatThrownBy(() -> FileFormat.decodeBalanceLine("MARTINA|CHF|10|50"))
                    .isInstanceOf(FileFormatException.class)
                    .hasMessageContaining("CHF");
        }

        @Test
        @DisplayName("decodeBalanceLine() throws FileFormatException for non-integer count")
        void decode_nonIntegerCount_throws() {
            assertThatThrownBy(() -> FileFormat.decodeBalanceLine("MARTINA|BGN|10|abc"))
                    .isInstanceOf(FileFormatException.class);
        }
    }

    // ── transaction file codec ────────────────────────────────────────────────

    @Nested
    @DisplayName("Transaction file codec")
    class TransactionCodec {

        @Test
        @DisplayName("encodeTransactionLine() produces correct pipe-delimited format")
        void encode_deposit_bgn() {
            Transaction tx = Transaction.of("MARTINA", DEPOSIT, BGN, 600,
                    List.of(new Denomination(10, 10), new Denomination(50, 10)), TS);

            String line = FileFormat.encodeTransactionLine(tx);

            assertThat(line).isEqualTo("2025-04-29T10:00:00|MARTINA|DEPOSIT|BGN|600|10x10,50x10");
        }

        @Test
        @DisplayName("encodeTransactionLine() encodes withdrawal EUR correctly")
        void encode_withdrawal_eur() {
            Transaction tx = Transaction.of("PETER", WITHDRAWAL, EUR, 500,
                    List.of(new Denomination(50, 10)), TS);

            assertThat(FileFormat.encodeTransactionLine(tx))
                    .isEqualTo("2025-04-29T10:00:00|PETER|WITHDRAWAL|EUR|500|50x10");
        }

        @Test
        @DisplayName("decodeTransactionLine() parses all six columns correctly")
        void decode_allColumnsCorrect() {
            Transaction tx = FileFormat.decodeTransactionLine("2025-04-29T10:00:00|MARTINA|DEPOSIT|BGN|600|10x10,10x50");

            assertThat(tx.timestamp()).isEqualTo(TS);
            assertThat(tx.cashierName()).isEqualTo("MARTINA");
            assertThat(tx.operationType()).isEqualTo(DEPOSIT);
            assertThat(tx.currency()).isEqualTo(BGN);
            assertThat(tx.amount()).isEqualTo(600);
            assertThat(tx.denominations()).hasSize(2);
        }

        @Test
        @DisplayName("encode → decode round-trip is lossless for deposit")
        void encode_decode_roundTrip_deposit() {
            Transaction original = Transaction.of("LINDA", DEPOSIT, EUR, 200,
                    List.of(new Denomination(20, 5), new Denomination(50, 2)), TS);

            Transaction decoded = FileFormat.decodeTransactionLine(
                                        FileFormat.encodeTransactionLine(original));

            assertThat(decoded.timestamp()).isEqualTo(original.timestamp());
            assertThat(decoded.cashierName()).isEqualTo(original.cashierName());
            assertThat(decoded.operationType()).isEqualTo(original.operationType());
            assertThat(decoded.currency()).isEqualTo(original.currency());
            assertThat(decoded.amount()).isEqualTo(original.amount());
            assertThat(decoded.denominations()).hasSize(original.denominations().size());
        }

        @Test
        @DisplayName("encode → decode round-trip is lossless for withdrawal")
        void encode_decode_roundTrip_withdrawal() {
            Transaction original = Transaction.of("MARTINA", WITHDRAWAL, BGN, 100,
                    List.of(new Denomination(10, 5), new Denomination(50, 1)), TS);

            Transaction decoded = FileFormat.decodeTransactionLine(
                    FileFormat.encodeTransactionLine(original));

            assertThat(decoded.cashierName()).isEqualTo("MARTINA");
            assertThat(decoded.amount()).isEqualTo(100);
            assertThat(decoded.denominations()).hasSize(2);
        }

        @Test
        @DisplayName("decodeTransactionLine() throws FileFormatException for too few columns")
        void decode_tooFewColumns_throws() {
            assertThatThrownBy(() ->
                    FileFormat.decodeTransactionLine("2025-04-29T10:00:00|MARTINA|DEPOSIT|BGN|600"))
                    .isInstanceOf(FileFormatException.class)
                    .hasMessageContaining("expected " + FileFormat.TX_COL_COUNT_TOTAL);
        }

        @Test
        @DisplayName("decodeTransactionLine() throws FileFormatException for malformed timestamp")
        void decode_badTimestamp_throws() {
            assertThatThrownBy(() ->
                    FileFormat.decodeTransactionLine("29-04-2025|MARTINA|DEPOSIT|BGN|600|10x10"))
                    .isInstanceOf(FileFormatException.class)
                    .hasMessageContaining("29-04-2025");
        }

        @Test
        @DisplayName("decodeTransactionLine() throws FileFormatException for unknown OperationType")
        void decode_unknownOperationType_throws() {
            assertThatThrownBy(() ->
                    FileFormat.decodeTransactionLine(
                            "2025-04-29T10:00:00|MARTINA|TRANSFER|BGN|600|10x10"))
                    .isInstanceOf(FileFormatException.class)
                    .hasMessageContaining("TRANSFER");
        }
    }

    // ── timestamp format ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Timestamp format")
    class TimestampFormat {

        @Test
        @DisplayName("TIMESTAMP_FORMATTER serialises to yyyy-MM-dd'T'HH:mm:ss pattern")
        void formatter_serialisesCorrectly() {
            String formatted = TS.format(FileFormat.TIMESTAMP_FORMATTER);
            assertThat(formatted).isEqualTo("2025-04-29T10:00:00");
        }

        @Test
        @DisplayName("TIMESTAMP_FORMATTER parses its own output (round-trip)")
        void formatter_parseRoundTrip() {
            String serialised = TS.format(FileFormat.TIMESTAMP_FORMATTER);
            LocalDateTime parsed = LocalDateTime.parse(serialised, FileFormat.TIMESTAMP_FORMATTER);
            assertThat(parsed).isEqualTo(TS);
        }

        @Test
        @DisplayName("TIMESTAMP_FORMATTER preserves seconds-level precision")
        void formatter_secondsPrecision() {
            LocalDateTime withSeconds = LocalDateTime.of(2025, 4, 29, 10, 5, 37);
            String formatted = withSeconds.format(FileFormat.TIMESTAMP_FORMATTER);
            assertThat(formatted).isEqualTo("2025-04-29T10:05:37");
        }
    }

    // ── header constants ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Header constants")
    class Headers {

        @Test
        @DisplayName("BAL_HEADER starts with comment prefix")
        void balHeader_isComment() {
            assertThat(FileFormat.BAL_HEADER).startsWith(FileFormat.COMMENT_PREFIX);
        }

        @Test
        @DisplayName("TX_HEADER starts with comment prefix")
        void txHeader_isComment() {
            assertThat(FileFormat.TX_HEADER).startsWith(FileFormat.COMMENT_PREFIX);
        }

        @Test
        @DisplayName("BAL_HEADER is skippable by the parser")
        void balHeader_isSkippable() {
            assertThat(FileFormat.isSkippable(FileFormat.BAL_HEADER)).isTrue();
        }

        @Test
        @DisplayName("TX_HEADER is skippable by the parser")
        void txHeader_isSkippable() {
            assertThat(FileFormat.isSkippable(FileFormat.TX_HEADER)).isTrue();
        }
    }
}