package bg.fibank.cashdesk.service;

import bg.fibank.cashdesk.dto.CashOperationRequest;
import bg.fibank.cashdesk.dto.CashOperationResponse;
import bg.fibank.cashdesk.exception.CashierNotFoundException;
import bg.fibank.cashdesk.model.*;
import bg.fibank.cashdesk.repository.BalanceFileRepository;
import bg.fibank.cashdesk.repository.TransactionFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashDeskServiceTest {

    @Mock
    private BalanceFileRepository balanceFileRepository;

    @Mock
    private TransactionFileRepository transactionFileRepository;

    @InjectMocks
    private CashDeskService cashDeskService;

    private CashierBalance martina;

    @BeforeEach
    void setUp() {
        martina = new CashierBalance("MARTINA");
        martina.addDenominations(Currency.BGN, List.of(new Denomination(10, 50), new Denomination(50, 10)));
        martina.addDenominations(Currency.EUR, List.of(new Denomination(10, 100), new Denomination(50, 20)));
    }

    @Test
    @DisplayName("performOperation: deposit BGN increases balance and logs transaction")
    void deposit_Bgn_Success() throws IOException {
        // Arrange
        when(balanceFileRepository.findByCashier("MARTINA")).thenReturn(Optional.of(martina));
        CashOperationRequest request = CashOperationRequest.builder()
                .cashierName("MARTINA")
                .operationType(OperationType.DEPOSIT)
                .currency(Currency.BGN)
                .amount(BigDecimal.valueOf(600))
                .denominations(List.of(new Denomination(10, 10), new Denomination(50, 10)))
                .build();

        // Act
        CashOperationResponse response = cashDeskService.performOperation(request);

        // Assert
        assertThat(response.getCashierName()).isEqualTo("MARTINA");
        assertThat(response.getTotals().get(Currency.BGN)).isEqualTo(1000 + 100 + 500); // 1600
        
        verify(balanceFileRepository).save(martina);
        
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionFileRepository).append(txCaptor.capture());
        Transaction tx = txCaptor.getValue();
        assertThat(tx.cashierName()).isEqualTo("MARTINA");
        assertThat(tx.operationType()).isEqualTo(OperationType.DEPOSIT);
        assertThat(tx.amount()).isEqualTo(600);
    }

    @Test
    @DisplayName("performOperation: withdrawal BGN decreases balance when funds available")
    void withdrawal_Bgn_Success() throws IOException {
        // Arrange
        when(balanceFileRepository.findByCashier("MARTINA")).thenReturn(Optional.of(martina));
        CashOperationRequest request = CashOperationRequest.builder()
                .cashierName("MARTINA")
                .operationType(OperationType.WITHDRAWAL)
                .currency(Currency.BGN)
                .amount(BigDecimal.valueOf(100))
                .denominations(List.of(new Denomination(10, 5), new Denomination(50, 1)))
                .build();

        // Act
        CashOperationResponse response = cashDeskService.performOperation(request);

        // Assert
        assertThat(response.getTotals().get(Currency.BGN)).isEqualTo(1000 - 100); // 900
        verify(balanceFileRepository).save(martina);
        verify(transactionFileRepository).append(any());
    }

    @Test
    @DisplayName("performOperation: withdrawal fails when exact denominations not available")
    void withdrawal_InsufficientDenominations_ThrowsException() throws IOException {
        // Arrange
        when(balanceFileRepository.findByCashier("MARTINA")).thenReturn(Optional.of(martina));
        CashOperationRequest request = CashOperationRequest.builder()
                .cashierName("MARTINA")
                .operationType(OperationType.WITHDRAWAL)
                .currency(Currency.BGN)
                .amount(BigDecimal.valueOf(20))
                .denominations(List.of(new Denomination(20, 1))) // 20 BGN not in Martina's balance
                .build();

        // Act & Assert
        assertThatThrownBy(() -> cashDeskService.performOperation(request))
                .isInstanceOf(bg.fibank.cashdesk.exception.DenominationNotFoundException.class);
        
        verify(balanceFileRepository, never()).save(any());
        verify(transactionFileRepository, never()).append(any());
    }

    @Test
    @DisplayName("performOperation: fails when cashier not found")
    void performOperation_UnknownCashier_ThrowsException() {
        // Arrange
        when(balanceFileRepository.findByCashier("UNKNOWN")).thenReturn(Optional.empty());
        CashOperationRequest request = CashOperationRequest.builder()
                .cashierName("UNKNOWN")
                .amount(BigDecimal.TEN)
                .denominations(List.of(new Denomination(10, 1)))
                .build();

        // Act & Assert
        assertThatThrownBy(() -> cashDeskService.performOperation(request))
                .isInstanceOf(CashierNotFoundException.class);
    }

    @Test
    @DisplayName("performOperation: fails when amount does not match denominations")
    void performOperation_AmountMismatch_ThrowsException() {
        // Arrange
        when(balanceFileRepository.findByCashier("MARTINA")).thenReturn(Optional.of(martina));
        CashOperationRequest request = CashOperationRequest.builder()
                .cashierName("MARTINA")
                .operationType(OperationType.DEPOSIT)
                .currency(Currency.BGN)
                .amount(BigDecimal.valueOf(100))
                .denominations(List.of(new Denomination(10, 5))) // Total is 50, but request says 100
                .build();

        // Act & Assert
        assertThatThrownBy(() -> cashDeskService.performOperation(request))
                .isInstanceOf(bg.fibank.cashdesk.exception.InvalidOperationException.class)
                .hasMessageContaining("does not match denominations total");
    }

    @Test
    @DisplayName("getBalances: returns balances for all cashiers when name is null")
    void getBalances_All_Success() {
        // Arrange
        CashierBalance peter = new CashierBalance("PETER");
        when(balanceFileRepository.findAll()).thenReturn(List.of(martina, peter));
        
        // Act
        List<CashOperationResponse> result = cashDeskService.getBalances(null, null, null);

        // Assert
        assertThat(result).hasSize(2);
        verify(transactionFileRepository, times(2)).findAll(anyString(), any(), any());
    }

    @Test
    @DisplayName("getBalances: returns specific cashier with history filter")
    void getBalances_Filtered_Success() {
        // Arrange
        when(balanceFileRepository.findByCashier("MARTINA")).thenReturn(Optional.of(martina));
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 12, 31);
        
        // Act
        List<CashOperationResponse> result = cashDeskService.getBalances("MARTINA", from, to);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCashierName()).isEqualTo("MARTINA");
        verify(transactionFileRepository).findAll("MARTINA", from, to);
    }
}
