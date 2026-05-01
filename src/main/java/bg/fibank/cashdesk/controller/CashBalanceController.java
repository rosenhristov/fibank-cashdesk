package bg.fibank.cashdesk.controller;

import bg.fibank.cashdesk.dto.CashBalanceResponse;
import bg.fibank.cashdesk.service.CashDeskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST controller for querying cashier balances and denomination breakdowns.
 *
 * <p>All three query parameters are optional and may be combined freely.
 * When none are supplied all three cashiers are returned in insertion order
 * (MARTINA → PETER → LINDA).</p>
 *
 * <h2>Authentication</h2>
 * <p>All requests must carry the {@code FIB-X-AUTH} header with the configured
 * API key, enforced by {@code AuthHeaderFilter} before this controller is reached.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CashBalanceController {

    private final CashDeskService cashDeskService;

    /**
     * Returns current cashier balances with denomination breakdowns,
     * optionally filtered by cashier name and/or date range.
     *
     * <p>Query parameters (all optional):</p>
     * <ul>
     *   <li>{@code cashier}  — case-insensitive cashier name (MARTINA, PETER, LINDA)</li>
     *   <li>{@code dateFrom} — inclusive start date in {@code yyyy-MM-dd} format</li>
     *   <li>{@code dateTo}   — inclusive end date in {@code yyyy-MM-dd} format</li>
     * </ul>
     *
     * <p>HTTP response codes:</p>
     * <ul>
     *   <li>{@code 200 OK}          — query executed; body contains matched cashier entries</li>
     *   <li>{@code 401 Unauthorized} — missing or invalid {@code FIB-X-AUTH} header</li>
     *   <li>{@code 404 Not Found}    — {@code cashier} param provided but not found in system</li>
     * </ul>
     *
     * @param cashier  optional cashier name filter
     * @param dateFrom optional inclusive start date ({@code yyyy-MM-dd})
     * @param dateTo   optional inclusive end date ({@code yyyy-MM-dd})
     * @return balance response containing one entry per matched cashier
     */
    @GetMapping("/cash-balance")
    public ResponseEntity<CashBalanceResponse> cashBalance(@RequestParam(required = false)
                                                           String cashier,

                                                           @RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                           LocalDate dateFrom,


                                                           @RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                           LocalDate dateTo) {

        log.info("REQUEST | GET /api/v1/cash-balance | cashier={} | dateFrom={} | dateTo={}",
                cashier, dateFrom, dateTo);

        CashBalanceResponse response = cashDeskService.getBalances(cashier, dateFrom, dateTo);
        return ResponseEntity.ok(response);
    }
}