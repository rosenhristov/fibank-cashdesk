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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core business logic for all cash desk operations.
 *
 * <h2>Operations</h2>
 * <ul>
 *   <li>{@link #performOperation} — handles both deposits and withdrawals via a
 *       single method; the {@code operationType} field in the request
 *       distinguishes them.</li>
 *   <li>{@link #getBalances} — returns current live balances (with optional
 *       cashier-name and date-range filters).</li>
 * </ul>
 *
 * <h2>Per-cashier locking</h2>
 * <p>A {@link ConcurrentHashMap} holds one lock object per cashier name.
 * {@link #performOperation} synchronises on the cashier's lock for the entire
 * read → mutate → save → append sequence, so two concurrent requests for the
 * <em>same</em> cashier are serialised while requests for <em>different</em>
 * cashiers proceed in parallel.</p>
 *
 * <h2>Amount validation</h2>
 * <p>The stated {@code amount} in the request must equal the arithmetic sum of
 * {@code faceValue × count} across all supplied denominations. Mismatches are
 * rejected with {@link InvalidOperationException} before any balance state is
 * touched.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashDeskService {

    private final BalanceFileRepository balanceFileRepository;
    private final TransactionFileRepository transactionFileRepository;

    /** One lock object per cashier name; created lazily on first access. */
    private final ConcurrentHashMap<String, Object> cashierLocks = new ConcurrentHashMap<>();

    /**
     * Executes a deposit or withdrawal for the specified cashier.
     *
     * <p>Processing steps:</p>
     * <ol>
     *   <li>Normalise cashier name to upper-case.</li>
     *   <li>Verify cashier exists ({@link CashierNotFoundException} if not).</li>
     *   <li>Verify denomination sum matches stated amount
     *       ({@link InvalidOperationException} if not).</li>
     *   <li>Acquire the per-cashier lock.</li>
     *   <li>Reload balance inside the lock (guards against a concurrent save).</li>
     *   <li>Apply the operation on the domain object (exceptions from
     *       {@code CashierBalance} propagate naturally).</li>
     *   <li>Persist updated balance atomically.</li>
     *   <li>Append transaction record.</li>
     *   <li>Log the completed operation.</li>
     *   <li>Return a response with the updated currency balance.</li>
     * </ol>
     *
     * @param request validated request (validation enforced at the controller level)
     * @return response containing updated balance for the affected currency
     * @throws CashierNotFoundException   if the cashier name is not in the system
     * @throws InvalidOperationException  if denomination sum ≠ stated amount
     * @throws bg.fibank.cashdesk.exception.InsufficientFundsException
     *                                   if a withdrawal exceeds available counts
     * @throws bg.fibank.cashdesk.exception.DenominationNotFoundException
     *                                   if a withdrawal requests a non-existent face value
     */
    public CashOperationResponse performOperation(CashOperationRequest request) {
        String cashierName = request.cashierName().trim().toUpperCase();

        if (!balanceFileRepository.exists(cashierName)) {
            throw new CashierNotFoundException(cashierName);
        }

        List<Denomination> denominations = toDomain(request.denominations());
        validateDenominationSum(cashierName, request.amount(), denominations);

        Object lock = cashierLocks.computeIfAbsent(cashierName, k -> new Object());
        synchronized (lock) {
            CashierBalance balance = balanceFileRepository.findByCashier(cashierName)
                    .orElseThrow(() -> new CashierNotFoundException(cashierName));

            applyOperation(request.operationType(), request.currency(),
                    denominations, balance, cashierName);

            try {
                balanceFileRepository.save(balance);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to persist balance for " + cashierName, e);
            }

            Transaction tx = Transaction.of(
                    cashierName,
                    request.operationType(),
                    request.currency(),
                    request.amount(),
                    denominations.stream().map(Denomination::copy).toList(),
                    LocalDateTime.now()
            );
            transactionFileRepository.append(tx);

            log.info("OPERATION | type={} | cashier={} | currency={} | amount={} | denominations={}",
                    request.operationType(), cashierName,
                    request.currency(), request.amount(),
                    tx.denominationsAsString());

            return buildOperationResponse(cashierName, request.operationType(),
                    request.currency(), balance);
        }
    }

    /**
     * Returns live cashier balances, optionally filtered by cashier name and/or
     * date range.
     *
     * <p>All three parameters are optional. When {@code cashierName} is supplied,
     * only that cashier's entry is returned; a non-existent name raises
     * {@link CashierNotFoundException}. The date parameters are echoed back in
     * the response so callers can confirm what filters were applied.</p>
     *
     * @param cashierName optional cashier filter (case-insensitive)
     * @param dateFrom    optional inclusive start date
     * @param dateTo      optional inclusive end date
     * @return balance response containing one entry per matched cashier
     */
    public CashBalanceResponse getBalances(String cashierName,
                                           LocalDate dateFrom,
                                           LocalDate dateTo) {
        List<CashierBalance> balances;

        if (cashierName != null && !cashierName.isBlank()) {
            String upper = cashierName.trim().toUpperCase();
            CashierBalance single = balanceFileRepository.findByCashier(upper)
                    .orElseThrow(() -> new CashierNotFoundException(upper));
            balances = List.of(single);
        } else {
            balances = balanceFileRepository.findAll();
        }

        List<CashierBalanceDto> dtos = balances.stream()
                .map(this::toCashierBalanceDto)
                .toList();

        log.info("BALANCE_QUERY | cashier={} | dateFrom={} | dateTo={} | results={}",
                cashierName, dateFrom, dateTo, dtos.size());

        return new CashBalanceResponse(dtos, cashierName, dateFrom, dateTo);
    }


    /**
     * Validates that the sum of {@code faceValue × count} across all supplied
     * denominations equals the stated {@code amount}.
     */
    private void validateDenominationSum(String cashierName,
                                         int statedAmount,
                                         List<Denomination> denominations) {
        int sum = denominations.stream()
                .mapToInt(d -> d.getFaceValue() * d.getCount())
                .sum();

        if (sum != statedAmount) {
            throw new InvalidOperationException(
                    "Denomination sum %d does not match stated amount %d for cashier '%s'."
                            .formatted(sum, statedAmount, cashierName));
        }
    }

    /**
     * Applies a deposit or withdrawal to the in-memory {@link CashierBalance}.
     * Domain-level exceptions ({@code InsufficientFundsException},
     * {@code DenominationNotFoundException}) propagate unchanged.
     */
    private void applyOperation(OperationType type,
                                Currency currency,
                                List<Denomination> denominations,
                                CashierBalance balance,
                                String cashierName) {
        switch (type) {
            case DEPOSIT    -> balance.addDenominations(currency, denominations);
            case WITHDRAWAL -> balance.subtractDenominations(currency, denominations);
            default         -> throw new InvalidOperationException(
                    "Unknown operation type '%s' for cashier '%s'.".formatted(type, cashierName));
        }
    }

    /** Converts a list of {@link DenominationDto} to domain {@link Denomination} objects. */
    private List<Denomination> toDomain(List<DenominationDto> dtos) {
        return dtos.stream()
                .map(d -> new Denomination(d.faceValue(), d.count()))
                .toList();
    }

    /** Builds the operation response for the affected currency. */
    private CashOperationResponse buildOperationResponse(String cashierName,
                                                         OperationType operationType,
                                                         Currency currency,
                                                         CashierBalance balance) {
        CurrencyBalanceDto currencyBalance = toCurrencyBalanceDto(balance, currency);
        return new CashOperationResponse(
                cashierName,
                operationType,
                currency,
                currencyBalance.total(),
                currencyBalance.denominations()
        );
    }

    /** Maps one {@link CashierBalance} to its full DTO representation. */
    private CashierBalanceDto toCashierBalanceDto(CashierBalance balance) {
        return new CashierBalanceDto(
                balance.getCashierName(),
                toCurrencyBalanceDto(balance, Currency.BGN),
                toCurrencyBalanceDto(balance, Currency.EUR)
        );
    }

    /** Maps one currency's denomination list to a {@link CurrencyBalanceDto}. */
    private CurrencyBalanceDto toCurrencyBalanceDto(CashierBalance balance, Currency currency) {
        List<DenominationDto> denomDtos = balance.getDenominationsForCurrency(currency).stream()
                .map(d -> new DenominationDto(d.getFaceValue(), d.getCount()))
                .toList();
        return new CurrencyBalanceDto(balance.getTotalForCurrency(currency), denomDtos);
    }
}
