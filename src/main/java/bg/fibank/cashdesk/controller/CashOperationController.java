package bg.fibank.cashdesk.controller;

import bg.fibank.cashdesk.dto.CashOperationRequest;
import bg.fibank.cashdesk.dto.CashOperationResponse;
import bg.fibank.cashdesk.service.CashDeskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for cash operations (deposits and withdrawals).
 *
 * <p>A single endpoint handles both operation types — the
 * {@code operationType} field in the request body ({@code "DEPOSIT"} or
 * {@code "WITHDRAWAL"}) distinguishes them at runtime.  This matches the
 * spec requirement: <em>"Deposits and withdrawals must be part of one and
 * the same API method."</em></p>
 *
 * <h2>Authentication</h2>
 * <p>All requests must carry the {@code FIB-X-AUTH} header with the configured
 * API key.  This is enforced by {@code AuthHeaderFilter} before the request
 * reaches this controller.</p>
 *
 * <h2>Validation</h2>
 * <p>{@code @Valid} on the {@code @RequestBody} parameter triggers Jakarta Bean
 * Validation on {@link CashOperationRequest} and its nested
 * {@code DenominationDto} list.  Any violation produces a {@code 400} response
 * via {@code GlobalExceptionHandler.handleValidation}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CashOperationController {

    private final CashDeskService cashDeskService;

    /**
     * Executes a deposit or withdrawal for the specified cashier.
     *
     * <p>Request body fields:</p>
     * <ul>
     *   <li>{@code cashierName}   — must not be blank</li>
     *   <li>{@code operationType} — {@code DEPOSIT} or {@code WITHDRAWAL}</li>
     *   <li>{@code currency}      — {@code BGN} or {@code EUR}</li>
     *   <li>{@code amount}        — positive integer; must equal the denomination sum</li>
     *   <li>{@code denominations} — non-empty list of {@code {faceValue, count}} objects</li>
     * </ul>
     *
     * <p>HTTP response codes:</p>
     * <ul>
     *   <li>{@code 200 OK}                   — operation applied successfully</li>
     *   <li>{@code 400 Bad Request}           — validation failure or malformed JSON</li>
     *   <li>{@code 401 Unauthorized}          — missing or invalid {@code FIB-X-AUTH} header</li>
     *   <li>{@code 404 Not Found}             — cashier name not in the system</li>
     *   <li>{@code 422 Unprocessable Entity}  — amount/denomination mismatch or insufficient funds</li>
     *   <li>{@code 500 Internal Server Error} — storage failure</li>
     * </ul>
     *
     * @param request validated request body
     * @return updated balance for the affected currency
     */
    @PostMapping("/cash-operation")
    public ResponseEntity<CashOperationResponse> cashOperation(@Valid @RequestBody CashOperationRequest request) {

        log.info("REQUEST | POST /api/v1/cash-operation | cashier={} | type={} | currency={} | amount={}",
                request.cashierName(), request.operationType(), request.currency(), request.amount());

        CashOperationResponse response = cashDeskService.performOperation(request);

        return ResponseEntity.ok(response);
    }
}

