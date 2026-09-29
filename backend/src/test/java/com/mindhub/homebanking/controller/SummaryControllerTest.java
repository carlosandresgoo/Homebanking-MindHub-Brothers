package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.domain.TransactionCategory;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SummaryControllerTest extends IntegrationTest {

    private static final String URL = "/api/clients/current/summary";

    @Autowired
    private Clock clock;

    private void transfer(String token, String to, String amount) throws Exception {
        mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceAccountNumber", "VIN001", "targetAccountNumber", to,
                                "amount", amount))))
                .andExpect(status().isCreated());
    }

    @Test
    void summarisesIncomeExpensesCategoriesAndBalanceLeavingOutOwnTransfers() throws Exception {
        LocalDate lastMonth = YearMonth.now(clock).minusMonths(1).atDay(5);
        testData.account(TestData.CLIENT_EMAIL, "VIN002", BigDecimal.ZERO);
        testData.movement("VIN001", true, "1000.00", TransactionCategory.DEPOSIT, "Sueldo", lastMonth.atTime(9, 0));
        testData.movement("VIN001", false, "200.00", TransactionCategory.OTHER, "Súper", lastMonth.atTime(18, 0));
        String token = accessToken(TestData.CLIENT_EMAIL);
        transfer(token, "VIN002", "300.00"); // own accounts: not income nor expense
        transfer(token, "VIN999", "100.00");

        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value(5700.00))
                .andExpect(jsonPath("$.months", hasSize(6)))
                // This month: the initial deposit in, the transfer to another client out.
                .andExpect(jsonPath("$.months[5].income").value(5000.00))
                .andExpect(jsonPath("$.months[5].expense").value(100.00))
                .andExpect(jsonPath("$.months[4].month").value(lastMonth.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.months[4].income").value(1000.00))
                .andExpect(jsonPath("$.months[4].expense").value(200.00))
                .andExpect(jsonPath("$.months[3].income").value(0))
                .andExpect(jsonPath("$.expenses", hasSize(2)))
                .andExpect(jsonPath("$.expenses[0].category").value("OTHER"))
                .andExpect(jsonPath("$.expenses[0].amount").value(200.00))
                .andExpect(jsonPath("$.expenses[1].category").value("TRANSFER_OUT"))
                // Daily balance: today's total, and yesterday's without today's movements.
                .andExpect(jsonPath("$.balanceHistory[-1:].balance").value(5700.00))
                .andExpect(jsonPath("$.balanceHistory[-2:-1].balance").value(800.00));
    }

    @Test
    void validatesTheNumberOfMonths() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get(URL).param("months", "1").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.months", hasSize(1)));
        mvc.perform(get(URL).param("months", "13").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("months", "0").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyClientsHaveASummary() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isForbidden());
    }
}
