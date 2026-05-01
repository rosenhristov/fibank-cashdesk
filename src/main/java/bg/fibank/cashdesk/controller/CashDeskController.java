package bg.fibank.cashdesk.controller;

import bg.fibank.cashdesk.dto.CashBalanceRequest;
import bg.fibank.cashdesk.dto.CashBalanceResponse;
import bg.fibank.cashdesk.dto.CashOperationRequest;
import bg.fibank.cashdesk.dto.CashOperationResponse;
import bg.fibank.cashdesk.service.CashDeskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CashDeskController {

    private final CashDeskService cashDeskService;

//    @PostMapping("/cash-operation")
//    public CashOperationResponse performOperation(@Valid @RequestBody CashOperationRequest request) {
//        return cashDeskService.performOperation(request);
//    }
//
//    @GetMapping("/cash-balance")
//    public CashBalanceResponse getBalances(@Valid CashBalanceRequest request) {
//        return cashDeskService.getBalances(request);
//    }
}
