package bg.fibank.cashdesk.service;

import bg.fibank.cashdesk.dto.CashOperationRequest;
import bg.fibank.cashdesk.dto.CashOperationResponse;
import bg.fibank.cashdesk.exception.CashierNotFoundException;
import bg.fibank.cashdesk.exception.InvalidOperationException;
import bg.fibank.cashdesk.model.*;
import bg.fibank.cashdesk.repository.BalanceFileRepository;
import bg.fibank.cashdesk.repository.TransactionFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashDeskService {

    private final BalanceFileRepository balanceFileRepository;
    private final TransactionFileRepository transactionFileRepository;

    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public CashOperationResponse performOperation(CashOperationRequest request) {
        String cashierName = request.getCashierName().toUpperCase();
        
        synchronized (getLock(cashierName)) {
            CashierBalance balance = balanceFileRepository.findByCashier(cashierName)
                    .orElseThrow(() -> new CashierNotFoundException(cashierName));

            List<Denomination> denoms = request.getDenominations();
            int calculatedAmount = denoms.stream().mapToInt(Denomination::total).sum();

            if (request.getAmount().compareTo(BigDecimal.valueOf(calculatedAmount)) != 0) {
                throw new InvalidOperationException(String.format(
                        "Request amount (%s) does not match denominations total (%d)",
                        request.getAmount(), calculatedAmount));
            }

            if (request.getOperationType() == OperationType.WITHDRAWAL) {
                balance.subtractDenominations(request.getCurrency(), denoms);
            } else {
                balance.addDenominations(request.getCurrency(), denoms);
            }

            try {
                balanceFileRepository.save(balance);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to save balance for cashier " + cashierName, e);
            }

            Transaction tx = Transaction.of(
                    cashierName,
                    request.getOperationType(),
                    request.getCurrency(),
                    calculatedAmount,
                    request.getDenominations(),
                    LocalDateTime.now()
            );
            transactionFileRepository.append(tx);

            log.info("OPERATION | cashier={} type={} currency={} amount={}",
                    cashierName, request.getOperationType(), request.getCurrency(), calculatedAmount);

            return buildResponse(balance, null);
        }
    }

    public List<CashOperationResponse> getBalances(String cashier, LocalDate dateFrom, LocalDate dateTo) {
        log.info("QUERY | cashier={} dateFrom={} dateTo={}", cashier, dateFrom, dateTo);

        List<CashierBalance> balances;
        if (cashier != null && !cashier.isBlank()) {
            String cashierUpper = cashier.toUpperCase();
            balances = balanceFileRepository.findByCashier(cashierUpper)
                    .map(List::of)
                    .orElseThrow(() -> new CashierNotFoundException(cashierUpper));
        } else {
            balances = balanceFileRepository.findAll();
        }

        return balances.stream()
                .map(b -> {
                    List<Transaction> history = transactionFileRepository.findAll(b.getCashierName(), dateFrom, dateTo);
                    return buildResponse(b, history);
                })
                .toList();
    }

    private CashOperationResponse buildResponse(CashierBalance balance, List<Transaction> history) {
        Map<Currency, Integer> totals = new EnumMap<>(Currency.class);
        Map<Currency, List<Denomination>> denoms = new EnumMap<>(Currency.class);

        for (Currency cur : Currency.values()) {
            totals.put(cur, balance.getTotalForCurrency(cur));
            denoms.put(cur, balance.getDenominationsForCurrency(cur));
        }

        return CashOperationResponse.builder()
                .cashierName(balance.getCashierName())
                .totals(totals)
                .denominations(denoms)
                .history(history)
                .build();
    }

    private Object getLock(String cashierName) {
        return locks.computeIfAbsent(cashierName, k -> new Object());
    }
}
