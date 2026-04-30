package bg.fibank.cashdesk.service;

import bg.fibank.cashdesk.dto.*;
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
import java.util.stream.Collectors;

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

            List<Denomination> domainDenoms = request.getDenominations().stream()
                    .map(d -> new Denomination(d.getFaceValue(), d.getCount()))
                    .toList();

            int calculatedAmount = domainDenoms.stream().mapToInt(Denomination::total).sum();

            if (request.getAmount().compareTo(BigDecimal.valueOf(calculatedAmount)) != 0) {
                throw new InvalidOperationException(String.format(
                        "Request amount (%s) does not match denominations total (%d)",
                        request.getAmount(), calculatedAmount));
            }

            if (request.getOperationType() == OperationType.WITHDRAWAL) {
                balance.subtractDenominations(request.getCurrency(), domainDenoms);
            } else {
                balance.addDenominations(request.getCurrency(), domainDenoms);
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
                    domainDenoms,
                    LocalDateTime.now()
            );
            transactionFileRepository.append(tx);

            log.info("OPERATION | cashier={} type={} currency={} amount={}",
                    cashierName, request.getOperationType(), request.getCurrency(), calculatedAmount);

            return CashOperationResponse.builder()
                    .cashierName(balance.getCashierName())
                    .currency(request.getCurrency())
                    .updatedBalance(balance.getTotalForCurrency(request.getCurrency()))
                    .denominations(balance.getDenominationsForCurrency(request.getCurrency()).stream()
                            .map(d -> new DenominationDto(d.getFaceValue(), d.getCount()))
                            .toList())
                    .build();
        }
    }

    public CashBalanceResponse getBalances(CashBalanceRequest request) {
        String cashier = request.getCashier();
        LocalDate dateFrom = request.getDateFrom();
        LocalDate dateTo = request.getDateTo();

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

        List<CashierBalanceEntry> entries = balances.stream()
                .map(this::mapToEntry)
                .toList();

        return new CashBalanceResponse(entries);
    }

    private CashierBalanceEntry mapToEntry(CashierBalance balance) {
        Map<Currency, Integer> totals = new EnumMap<>(Currency.class);
        Map<Currency, List<DenominationDto>> denoms = new EnumMap<>(Currency.class);

        for (Currency cur : Currency.values()) {
            totals.put(cur, balance.getTotalForCurrency(cur));
            denoms.put(cur, balance.getDenominationsForCurrency(cur).stream()
                    .map(d -> new DenominationDto(d.getFaceValue(), d.getCount()))
                    .toList());
        }

        return new CashierBalanceEntry(balance.getCashierName(), totals, denoms);
    }

    private Object getLock(String cashierName) {
        return locks.computeIfAbsent(cashierName, k -> new Object());
    }
}
