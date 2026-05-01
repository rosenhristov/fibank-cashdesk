package bg.fibank.cashdesk.controller;

import bg.fibank.cashdesk.dto.*;
import bg.fibank.cashdesk.exception.CashierNotFoundException;
import bg.fibank.cashdesk.exception.GlobalExceptionHandler;
import bg.fibank.cashdesk.exception.InsufficientFundsException;
import bg.fibank.cashdesk.exception.InvalidOperationException;
import bg.fibank.cashdesk.filter.AuthHeaderFilter;
import bg.fibank.cashdesk.model.Currency;
import bg.fibank.cashdesk.model.OperationType;
import bg.fibank.cashdesk.service.CashDeskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("CashOperationController Tests")
class CashOperationControllerTest {

    private static final String URL        = "/api/v1/cash-operation";
    private static final String VALID_KEY  = "f9Uie8nNf112hx8s";
    private static final String AUTH_HEADER = "FIB-X-AUTH";

    private MockMvc mockMvc;
    private CashDeskService cashDeskService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        cashDeskService = mock(CashDeskService.class);
        CashOperationController controller = new CashOperationController(cashDeskService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new AuthHeaderFilter(mockAppProperties()))
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    private bg.fibank.cashdesk.config.AppProperties mockAppProperties() {
        bg.fibank.cashdesk.config.AppProperties props = mock(bg.fibank.cashdesk.config.AppProperties.class);
        bg.fibank.cashdesk.config.AppProperties.Auth auth = new bg.fibank.cashdesk.config.AppProperties.Auth();
        auth.setApiKey(VALID_KEY);
        when(props.getAuth()).thenReturn(auth);
        return props;
    }

    // ── auth ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authentication")
    class Auth {

        @Test
        @DisplayName("missing FIB-X-AUTH header → 401")
        void missingHeader_returns401() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validDepositBgnJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("wrong FIB-X-AUTH value → 401")
        void wrongKey_returns401() throws Exception {
            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, "wrong-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validDepositBgnJson()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("valid deposit request → 200 with operation response body")
        void validDeposit_returns200() throws Exception {
            when(cashDeskService.performOperation(any())).thenReturn(depositBgnResponse());

            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validDepositBgnJson()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.cashierName").value("MARTINA"))
                    .andExpect(jsonPath("$.operationType").value("DEPOSIT"))
                    .andExpect(jsonPath("$.currency").value("BGN"))
                    .andExpect(jsonPath("$.newBalance").value(1600));
        }

        @Test
        @DisplayName("valid withdrawal request → 200")
        void validWithdrawal_returns200() throws Exception {
            when(cashDeskService.performOperation(any())).thenReturn(withdrawEurResponse());

            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validWithdrawalEurJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.operationType").value("WITHDRAWAL"))
                    .andExpect(jsonPath("$.newBalance").value(1500));
        }
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation errors → 400")
    class ValidationErrors {

        @Test
        @DisplayName("blank cashierName → 400 with violation")
        void blankCashierName_returns400() throws Exception {
            String body = """
                    {"cashierName":"","operationType":"DEPOSIT","currency":"BGN",
                     "amount":100,"denominations":[{"faceValue":10,"count":10}]}""";

            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.violations").isArray())
                    .andExpect(jsonPath("$.violations[0].field").value("cashierName"));
        }

        @Test
        @DisplayName("missing operationType → 400")
        void missingOperationType_returns400() throws Exception {
            String body = """
                    {"cashierName":"MARTINA","currency":"BGN",
                     "amount":100,"denominations":[{"faceValue":10,"count":10}]}""";

            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("invalid operationType enum value → 400")
        void invalidEnumValue_returns400() throws Exception {
            String body = """
                    {"cashierName":"MARTINA","operationType":"TRANSFER","currency":"BGN",
                     "amount":100,"denominations":[{"faceValue":10,"count":10}]}""";

            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("empty denominations list → 400")
        void emptyDenominations_returns400() throws Exception {
            String body = """
                    {"cashierName":"MARTINA","operationType":"DEPOSIT","currency":"BGN",
                     "amount":100,"denominations":[]}""";

            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("zero amount → 400")
        void zeroAmount_returns400() throws Exception {
            String body = """
                    {"cashierName":"MARTINA","operationType":"DEPOSIT","currency":"BGN",
                     "amount":0,"denominations":[{"faceValue":10,"count":10}]}""";

            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── domain errors ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Domain errors")
    class DomainErrors {

        @Test
        @DisplayName("unknown cashier → 404")
        void unknownCashier_returns404() throws Exception {
            when(cashDeskService.performOperation(any()))
                    .thenThrow(new CashierNotFoundException("NOBODY"));

            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validDepositBgnJson()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("NOBODY")));
        }

        @Test
        @DisplayName("insufficient funds → 422")
        void insufficientFunds_returns422() throws Exception {
            when(cashDeskService.performOperation(any()))
                    .thenThrow(new InsufficientFundsException("MARTINA", Currency.EUR, 50, 21, 20));

            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validWithdrawalEurJson()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
        }

        @Test
        @DisplayName("denomination sum mismatch → 422")
        void amountMismatch_returns422() throws Exception {
            when(cashDeskService.performOperation(any()))
                    .thenThrow(new InvalidOperationException("Sum mismatch"));

            mockMvc.perform(post(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validDepositBgnJson()))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private String validDepositBgnJson() {
        return """
                {"cashierName":"MARTINA","operationType":"DEPOSIT","currency":"BGN",
                 "amount":600,"denominations":[{"faceValue":10,"count":10},{"faceValue":50,"count":10}]}""";
    }

    private String validWithdrawalEurJson() {
        return """
                {"cashierName":"MARTINA","operationType":"WITHDRAWAL","currency":"EUR",
                 "amount":500,"denominations":[{"faceValue":50,"count":10}]}""";
    }

    private CashOperationResponse depositBgnResponse() {
        var denoms = List.of(new DenominationDto(10, 60), new DenominationDto(50, 20));
        return new CashOperationResponse("MARTINA", OperationType.DEPOSIT,
                Currency.BGN, 1600, denoms);
    }

    private CashOperationResponse withdrawEurResponse() {
        var denoms = List.of(new DenominationDto(10, 100), new DenominationDto(50, 10));
        return new CashOperationResponse("MARTINA", OperationType.WITHDRAWAL,
                Currency.EUR, 1500, denoms);
    }
}
