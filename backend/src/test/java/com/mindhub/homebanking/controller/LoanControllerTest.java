package com.mindhub.homebanking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanControllerTest extends IntegrationTest {

    private static final long MORTGAGE = 1;
    private static final long PERSONAL = 2;

    private MvcResult apply(String token, long loanId, Object amount, int payments, String account) throws Exception {
        return mvc.perform(post("/api/loans").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loanId", loanId, "amount", amount, "payments", payments,
                                "accountNumber", account))))
                .andReturn();
    }

    private MvcResult pay(String token, long clientLoanId, String account) throws Exception {
        return mvc.perform(post("/api/clients/current/loans/" + clientLoanId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("accountNumber", account))))
                .andReturn();
    }

    private JsonNode latestMovement(String token, long accountId) throws Exception {
        return body(mvc.perform(get("/api/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn()).get("content").get(0);
    }

    private JsonNode account(String token, long id) throws Exception {
        return body(mvc.perform(get("/api/accounts/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn());
    }

    @Test
    void catalogListsTheThreeProducts() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/api/loans").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].code").value("MORTGAGE"))
                .andExpect(jsonPath("$[0].maxAmount").value(500000.00))
                .andExpect(jsonPath("$[0].payments", contains(12, 24, 36, 48, 60)))
                .andExpect(jsonPath("$[1].payments", contains(6, 12, 24)))
                .andExpect(jsonPath("$[2].code").value("AUTOMOTIVE"));
        mvc.perform(get("/api/loans")).andExpect(status().isUnauthorized());
    }

    @Test
    void applyingCreditsTheAccountAndAddsTwentyPercentInterest() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        MvcResult result = apply(token, PERSONAL, "10000.00", 6, "vin001");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode loan = body(result);
        assertThat(loan.get("name").asText()).isEqualTo("Personal");
        assertThat(loan.get("totalDue").decimalValue()).isEqualByComparingTo("12000.00");
        assertThat(loan.get("nextInstallment").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(loan.get("outstanding").decimalValue()).isEqualByComparingTo("12000.00");
        assertThat(loan.get("paymentsMade").asInt()).isZero();

        JsonNode account = account(token, ids.accountId());
        assertThat(account.get("balance").decimalValue()).isEqualByComparingTo("15000.00");
        assertThat(latestMovement(token, ids.accountId()).get("description").asText())
                .isEqualTo("Préstamo Personal acreditado");

        mvc.perform(get("/api/clients/current/loans").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("PERSONAL"));
    }

    @Test
    void applicationRulesAreEnforced() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        assertThat(apply(token, PERSONAL, "100000.01", 6, "VIN001").getResponse().getStatus()).isEqualTo(422);
        assertThat(apply(token, PERSONAL, "1000", 7, "VIN001").getResponse().getStatus()).isEqualTo(422);
        assertThat(apply(token, 99, "1000", 6, "VIN001").getResponse().getStatus()).isEqualTo(404);
        // Crediting someone else's account is not allowed (and not confirmed).
        assertThat(apply(token, PERSONAL, "1000", 6, "VIN999").getResponse().getStatus()).isEqualTo(404);
        assertThat(apply(token, PERSONAL, "0.50", 6, "VIN001").getResponse().getStatus()).isEqualTo(400);

        assertThat(account(token, ids.accountId()).get("balance").decimalValue()).isEqualByComparingTo("5000.00");
    }

    @Test
    void onlyOneActiveLoanPerProduct() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        assertThat(apply(token, PERSONAL, "1000", 6, "VIN001").getResponse().getStatus()).isEqualTo(201);
        assertThat(apply(token, PERSONAL, "1000", 12, "VIN001").getResponse().getStatus()).isEqualTo(409);
        assertThat(apply(token, MORTGAGE, "1000", 12, "VIN001").getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void installmentsArePaidUntilTheLoanIsSettledThenItCanBeRequestedAgain() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        long loanId = body(apply(token, PERSONAL, "6000.00", 6, "VIN001")).get("id").asLong();

        MvcResult first = pay(token, loanId, "VIN001");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(first).get("paymentsMade").asInt()).isEqualTo(1);
        assertThat(body(first).get("outstanding").decimalValue()).isEqualByComparingTo("6000.00");
        assertThat(latestMovement(token, ids.accountId()).get("description").asText())
                .isEqualTo("Cuota 1/6 préstamo Personal");

        for (int i = 2; i <= 6; i++) {
            assertThat(pay(token, loanId, "VIN001").getResponse().getStatus()).isEqualTo(200);
        }
        JsonNode settled = body(mvc.perform(get("/api/clients/current/loans")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn()).get(0);
        assertThat(settled.get("paidOff").asBoolean()).isTrue();
        assertThat(settled.get("outstanding").decimalValue()).isZero();
        // 5000 + 6000 credited - 7200 repaid
        assertThat(account(token, ids.accountId()).get("balance").decimalValue()).isEqualByComparingTo("3800.00");

        assertThat(pay(token, loanId, "VIN001").getResponse().getStatus()).isEqualTo(422);
        assertThat(apply(token, PERSONAL, "1000", 6, "VIN001").getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void cannotPayWithoutFunds() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        long loanId = body(apply(token, PERSONAL, "1000.00", 6, "VIN001")).get("id").asLong();
        String empty = body(mvc.perform(post("/api/clients/current/accounts")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn()).get("number").asText();

        MvcResult result = pay(token, loanId, empty);
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).get("detail").asText()).isEqualTo("Insufficient funds");
    }

    @Test
    void cannotPayAnotherClientsLoanOrWithAnotherClientsAccount() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        long melbasLoan = body(apply(melba, PERSONAL, "1000.00", 6, "VIN001")).get("id").asLong();

        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        // task11 let any client pay (and so alter) any loan.
        assertThat(pay(other, melbasLoan, "VIN999").getResponse().getStatus()).isEqualTo(404);
        assertThat(pay(melba, melbasLoan, "VIN999").getResponse().getStatus()).isEqualTo(404);

        JsonNode loan = body(mvc.perform(get("/api/clients/current/loans")
                .header(HttpHeaders.AUTHORIZATION, bearer(melba))).andReturn()).get(0);
        assertThat(loan.get("paymentsMade").asInt()).isZero();
    }

    @Test
    void loansAreForClientsOnly() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        assertThat(apply(admin, PERSONAL, "1000", 6, "VIN001").getResponse().getStatus()).isEqualTo(403);
        mvc.perform(get("/api/clients/current/loans").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/loans").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }
}
