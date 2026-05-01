package bg.fibank.cashdesk.controller;

import bg.fibank.cashdesk.dto.CashBalanceRequest;
import bg.fibank.cashdesk.dto.CashBalanceResponse;
import bg.fibank.cashdesk.service.CashDeskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for querying cashier balances and denomination breakdowns.
 *
 * <p>All three query parameters are optional and may be combined freely.
 * When none are supplied all three cashiers are returned in insertion order
 * (MARTINA → PETER → LINDA).</p>
 *
 * <p>Query parameters are bound into a {@link CashBalanceRequest} record via
 * {@code @ModelAttribute}. Spring Framework 6.1+ supports record binding
 * through the canonical constructor, so each query-param name must match the
 * corresponding record component name exactly.</p>
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
     * <p>Query parameters (all optional — provided via {@link CashBalanceRequest}):</p>
     * <ul>
     *   <li>{@code cashier}  — case-insensitive cashier name (MARTINA, PETER, LINDA)</li>
     *   <li>{@code dateFrom} — inclusive start date in {@code yyyy-MM-dd} format</li>
     *   <li>{@code dateTo}   — inclusive end date in {@code yyyy-MM-dd} format</li>
     * </ul>
     *
     * <p>HTTP response codes:</p>
     * <ul>
     *   <li>{@code 200 OK}           — query executed; body contains matched cashier entries</li>
     *   <li>{@code 401 Unauthorized} — missing or invalid {@code FIB-X-AUTH} header</li>
     *   <li>{@code 404 Not Found}    — {@code cashier} param provided but not found in system</li>
     * </ul>
     *
     * @param request query parameters bound from the URL query string
     * @return balance response containing one entry per matched cashier
     */
    @GetMapping("/cash-balance")
    public ResponseEntity<CashBalanceResponse> cashBalance(
            @ModelAttribute CashBalanceRequest request) {

        log.info("REQUEST | GET /api/v1/cash-balance | cashier={} | dateFrom={} | dateTo={}",
                request.cashier(), request.dateFrom(), request.dateTo());

        CashBalanceResponse response = cashDeskService.getBalances(
                request.cashier(), request.dateFrom(), request.dateTo());
        return ResponseEntity.ok(response);
    }
}
