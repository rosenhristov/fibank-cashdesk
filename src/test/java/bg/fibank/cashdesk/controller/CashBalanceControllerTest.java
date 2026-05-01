package bg.fibank.cashdesk.controller;

import bg.fibank.cashdesk.config.AppProperties;
import bg.fibank.cashdesk.dto.*;
import bg.fibank.cashdesk.exception.CashierNotFoundException;
import bg.fibank.cashdesk.exception.GlobalExceptionHandler;
import bg.fibank.cashdesk.filter.AuthHeaderFilter;
import bg.fibank.cashdesk.service.CashDeskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CashBalanceController.class)
@Import({GlobalExceptionHandler.class, AuthHeaderFilter.class, AppProperties.class})
class CashBalanceControllerTest {

    private static final String URL         = "/api/v1/cash-balance";
    private static final String VALID_KEY   = "f9Uie8nNf112hx8s";
    private static final String AUTH_HEADER = "FIB-X-AUTH";

    @Autowired MockMvc mockMvc;
    @MockBean  CashDeskService cashDeskService;

    // ── auth ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authentication")
    class Auth {

        @Test
        @DisplayName("missing FIB-X-AUTH header → 401")
        void missingHeader_returns401() throws Exception {
            mockMvc.perform(get(URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("wrong FIB-X-AUTH value → 401")
        void wrongKey_returns401() throws Exception {
            mockMvc.perform(get(URL).header(AUTH_HEADER, "bad-key"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── no filters ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No filters — all cashiers")
    class NoFilters {

        @Test
        @DisplayName("returns 200 with all three cashiers")
        void noFilters_returns200_allCashiers() throws Exception {
            when(cashDeskService.getBalances(null, null, null))
                    .thenReturn(allCashiersResponse());

            mockMvc.perform(get(URL).header(AUTH_HEADER, VALID_KEY))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.cashiers").isArray())
                    .andExpect(jsonPath("$.cashiers.length()").value(3));
        }

        @Test
        @DisplayName("each cashier entry contains bgn and eur fields")
        void response_containsBgnAndEur() throws Exception {
            when(cashDeskService.getBalances(null, null, null))
                    .thenReturn(allCashiersResponse());

            mockMvc.perform(get(URL).header(AUTH_HEADER, VALID_KEY))
                    .andExpect(jsonPath("$.cashiers[0].bgn").exists())
                    .andExpect(jsonPath("$.cashiers[0].eur").exists())
                    .andExpect(jsonPath("$.cashiers[0].bgn.total").value(1000))
                    .andExpect(jsonPath("$.cashiers[0].eur.total").value(2000));
        }

        @Test
        @DisplayName("denomination breakdown is present for each currency")
        void response_containsDenominations() throws Exception {
            when(cashDeskService.getBalances(null, null, null))
                    .thenReturn(allCashiersResponse());

            mockMvc.perform(get(URL).header(AUTH_HEADER, VALID_KEY))
                    .andExpect(jsonPath("$.cashiers[0].bgn.denominations").isArray())
                    .andExpect(jsonPath("$.cashiers[0].bgn.denominations[0].faceValue").value(10))
                    .andExpect(jsonPath("$.cashiers[0].bgn.denominations[0].count").value(50));
        }
    }

    // ── cashier filter ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cashier filter")
    class CashierFilter {

        @Test
        @DisplayName("?cashier=MARTINA → 200 with single cashier entry")
        void cashierFilter_returnsSingleCashier() throws Exception {
            when(cashDeskService.getBalances("MARTINA", null, null))
                    .thenReturn(singleCashierResponse("MARTINA"));

            mockMvc.perform(get(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .param("cashier", "MARTINA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cashiers.length()").value(1))
                    .andExpect(jsonPath("$.cashiers[0].cashierName").value("MARTINA"));
        }

        @Test
        @DisplayName("?cashier=NOBODY → 404")
        void unknownCashier_returns404() throws Exception {
            when(cashDeskService.getBalances("NOBODY", null, null))
                    .thenThrow(new CashierNotFoundException("NOBODY"));

            mockMvc.perform(get(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .param("cashier", "NOBODY"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    // ── date filters ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Date filters")
    class DateFilters {

        @Test
        @DisplayName("?dateFrom=2025-01-01 → 200, filter echoed in response")
        void dateFromFilter_echoed() throws Exception {
            LocalDate from = LocalDate.of(2025, 1, 1);
            when(cashDeskService.getBalances(null, from, null))
                    .thenReturn(new CashBalanceResponse(List.of(), null, from, null));

            mockMvc.perform(get(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .param("dateFrom", "2025-01-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dateFrom").value("2025-01-01"));
        }

        @Test
        @DisplayName("?dateTo=2025-12-31 → 200, filter echoed in response")
        void dateToFilter_echoed() throws Exception {
            LocalDate to = LocalDate.of(2025, 12, 31);
            when(cashDeskService.getBalances(null, null, to))
                    .thenReturn(new CashBalanceResponse(List.of(), null, null, to));

            mockMvc.perform(get(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .param("dateTo", "2025-12-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dateTo").value("2025-12-31"));
        }

        @Test
        @DisplayName("?cashier=PETER&dateFrom=2025-04-01&dateTo=2025-04-30 → 200")
        void allFilters_combined() throws Exception {
            LocalDate from = LocalDate.of(2025, 4, 1);
            LocalDate to   = LocalDate.of(2025, 4, 30);
            when(cashDeskService.getBalances("PETER", from, to))
                    .thenReturn(singleCashierResponse("PETER"));

            mockMvc.perform(get(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .param("cashier", "PETER")
                            .param("dateFrom", "2025-04-01")
                            .param("dateTo",   "2025-04-30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cashiers[0].cashierName").value("PETER"));
        }
    }

    // ── CashBalanceRequest binding ────────────────────────────────────────────

    @Nested
    @DisplayName("CashBalanceRequest @ModelAttribute binding")
    class RequestBinding {

        @Test
        @DisplayName("query params are bound to CashBalanceRequest and forwarded to service correctly")
        void allParams_boundAndForwardedToService() throws Exception {
            LocalDate from = LocalDate.of(2025, 4, 1);
            LocalDate to   = LocalDate.of(2025, 4, 30);
            when(cashDeskService.getBalances("MARTINA", from, to))
                    .thenReturn(singleCashierResponse("MARTINA"));

            mockMvc.perform(get(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .param("cashier",  "MARTINA")
                            .param("dateFrom", "2025-04-01")
                            .param("dateTo",   "2025-04-30"))
                    .andExpect(status().isOk());

            // Verify the controller correctly unpacked the record and called the service
            verify(cashDeskService).getBalances("MARTINA", from, to);
        }

        @Test
        @DisplayName("omitting all params binds null for each CashBalanceRequest field")
        void noParams_allFieldsNullInService() throws Exception {
            when(cashDeskService.getBalances(null, null, null))
                    .thenReturn(allCashiersResponse());

            mockMvc.perform(get(URL).header(AUTH_HEADER, VALID_KEY))
                    .andExpect(status().isOk());

            verify(cashDeskService).getBalances(null, null, null);
        }

        @Test
        @DisplayName("dateFrom and dateTo are parsed as LocalDate from yyyy-MM-dd strings")
        void dateParams_parsedAsLocalDate() throws Exception {
            LocalDate from = LocalDate.of(2025, 1, 1);
            LocalDate to   = LocalDate.of(2025, 12, 31);
            when(cashDeskService.getBalances(null, from, to))
                    .thenReturn(new CashBalanceResponse(List.of(), null, from, to));

            mockMvc.perform(get(URL)
                            .header(AUTH_HEADER, VALID_KEY)
                            .param("dateFrom", "2025-01-01")
                            .param("dateTo",   "2025-12-31"))
                    .andExpect(status().isOk());

            // If dates were parsed incorrectly the mock would return null and the test would fail
            verify(cashDeskService).getBalances(null, from, to);
        }
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private CashierBalanceDto cashierDto(String name) {
        var bgnDenoms = List.of(new DenominationDto(10, 50), new DenominationDto(50, 10));
        var eurDenoms = List.of(new DenominationDto(10, 100), new DenominationDto(50, 20));
        return new CashierBalanceDto(name,
                new CurrencyBalanceDto(1000, bgnDenoms),
                new CurrencyBalanceDto(2000, eurDenoms));
    }

    private CashBalanceResponse allCashiersResponse() {
        return new CashBalanceResponse(
                List.of(cashierDto("MARTINA"), cashierDto("PETER"), cashierDto("LINDA")),
                null, null, null);
    }

    private CashBalanceResponse singleCashierResponse(String name) {
        return new CashBalanceResponse(List.of(cashierDto(name)), name, null, null);
    }
}
