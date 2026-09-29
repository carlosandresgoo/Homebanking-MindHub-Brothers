package com.mindhub.homebanking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindhub.homebanking.service.FixedTermService;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FixedTermControllerTest extends IntegrationTest {

    private static final String URL = "/api/clients/current/fixed-terms";

    @Autowired
    private FixedTermService fixedTermService;

    @Autowired
    private Clock clock;

    private ResultActions create(String token, String account, String amount, int days, boolean autoRenew)
            throws Exception {
        return mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("accountNumber", account, "amount", amount, "termDays", days,
                        "autoRenew", autoRenew))));
    }

    private BigDecimal balance(String token, long accountId) throws Exception {
        return body(mvc.perform(get("/api/accounts/" + accountId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn()).get("balance").decimalValue();
    }

    private JsonNode mine(String token) throws Exception {
        return body(mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn());
    }

    @Test
    void listsTheAvailableTermsAndRates() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/api/fixed-terms/plans").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].termDays").value(30))
                .andExpect(jsonPath("$[0].annualRate").value(0.35));
    }

    @Test
    void constitutesAFixedTermDebitingTheAccount() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        LocalDate today = LocalDate.now(clock);

        create(token, "vin001", "1000.00", 30, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.principal").value(1000.00))
                // 1,000 × 35 % × 30 / 365 = 28.767… → 28.77
                .andExpect(jsonPath("$.interest").value(28.77))
                .andExpect(jsonPath("$.total").value(1028.77))
                .andExpect(jsonPath("$.startDate").value(today.toString()))
                .andExpect(jsonPath("$.maturityDate").value(today.plusDays(30).toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(balance(token, ids.accountId())).isEqualByComparingTo("4000.00");
        mvc.perform(get("/api/accounts/" + ids.accountId() + "/transactions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[0].category").value("FIXED_TERM_DEPOSIT"))
                .andExpect(jsonPath("$.content[0].amount").value(1000.00));
    }

    @Test
    void enforcesMinimumTermsFundsAndOwnership() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        MvcResult tooSmall = create(token, "VIN001", "999.99", 30, false)
                .andExpect(status().isUnprocessableEntity()).andReturn();
        assertThat(body(tooSmall).get("code").asText()).isEqualTo("BELOW_MINIMUM");
        create(token, "VIN001", "1000.00", 45, false).andExpect(status().isUnprocessableEntity());
        create(token, "VIN001", "5000.01", 30, false).andExpect(status().isUnprocessableEntity());
        create(token, "VIN999", "1000.00", 30, false).andExpect(status().isNotFound());
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("accountNumber", "VIN001"))))
                .andExpect(status().isBadRequest());
        assertThat(balance(token, ids.accountId())).isEqualByComparingTo("5000.00");
    }

    @Test
    void paysPrincipalAndInterestAtMaturityOnlyOnce() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        create(token, "VIN001", "1000.00", 30, false).andExpect(status().isCreated());
        LocalDate today = LocalDate.now(clock);

        assertThat(fixedTermService.payDue(today.plusDays(29))).isZero(); // not yet
        assertThat(fixedTermService.payDue(today.plusDays(30))).isEqualTo(1);
        assertThat(fixedTermService.payDue(today.plusDays(31))).isZero(); // never twice

        assertThat(balance(token, ids.accountId())).isEqualByComparingTo("5028.77");
        JsonNode paid = mine(token).get(0);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("paidAt").isNull()).isFalse();
        mvc.perform(get("/api/accounts/" + ids.accountId() + "/transactions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[0].category").value("FIXED_TERM_INTEREST"))
                .andExpect(jsonPath("$.content[0].amount").value(28.77))
                .andExpect(jsonPath("$.content[1].category").value("FIXED_TERM_PAYOUT"));

        // Putting money in and getting it back is not income nor spending; the interest is income.
        mvc.perform(get("/api/clients/current/summary").param("months", "1").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.months[0].income").value(5028.77))
                .andExpect(jsonPath("$.months[0].expense").value(0))
                .andExpect(jsonPath("$.expenses", hasSize(0)));
    }

    @Test
    void automaticRenewalReinvestsPrincipalAndInterest() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        long id = body(create(token, "VIN001", "2000.00", 30, true).andReturn()).get("id").asLong();

        fixedTermService.payDue(LocalDate.now(clock).plusDays(30));

        JsonNode list = mine(token);
        assertThat(list).hasSize(2);
        JsonNode renewed = list.get(0); // active first
        assertThat(renewed.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(renewed.get("principal").decimalValue()).isEqualByComparingTo("2057.53"); // 2,000 + 57.53
        assertThat(renewed.get("autoRenew").asBoolean()).isTrue();
        assertThat(list.get(1).get("id").asLong()).isEqualTo(id);
        assertThat(list.get(1).get("status").asText()).isEqualTo("PAID");
        assertThat(balance(token, ids.accountId())).isEqualByComparingTo("3000.00");
    }

    @Test
    void autoRenewCanChangeOnlyWhileActiveAndOnlyByTheOwner() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        long id = body(create(melba, "VIN001", "1000.00", 60, false).andReturn()).get("id").asLong();

        mvc.perform(patch(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("autoRenew", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoRenew").value(true));

        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        mvc.perform(patch(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("autoRenew", false))))
                .andExpect(status().isNotFound());

        mvc.perform(patch(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("autoRenew", false))))
                .andExpect(status().isOk());
        fixedTermService.payDue(LocalDate.now(clock).plusDays(60));
        mvc.perform(patch(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("autoRenew", true))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void anAccountWithAnActiveFixedTermCannotBeClosed() throws Exception {
        long vin002 = testData.account(TestData.CLIENT_EMAIL, "VIN002", new BigDecimal("1000.00")).getId();
        String token = accessToken(TestData.CLIENT_EMAIL);
        create(token, "VIN002", "1000.00", 30, false).andExpect(status().isCreated());

        mvc.perform(delete("/api/accounts/" + vin002).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyClientsHaveFixedTerms() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isForbidden());
        create(admin, "VIN001", "1000.00", 30, false).andExpect(status().isForbidden());
    }
}
