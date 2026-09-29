package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Daily limit for transfers to other clients: 6,000 in the test profile (8,000 with 2FA). */
class TransferLimitTest extends IntegrationTest {

    private MvcResult transfer(String token, String from, String to, String amount) throws Exception {
        return mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceAccountNumber", from, "targetAccountNumber", to,
                                "amount", amount))))
                .andReturn();
    }

    @Test
    void limitsCountOnlyTodaysTransfersToOthers() throws Exception {
        testData.account(TestData.CLIENT_EMAIL, "VIN002", new BigDecimal("5000.00"));
        String token = accessToken(TestData.CLIENT_EMAIL);

        mvc.perform(get("/api/transfers/limits").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(6000))
                .andExpect(jsonPath("$.usedToday").value(0))
                .andExpect(jsonPath("$.remainingToday").value(6000))
                .andExpect(jsonPath("$.secondFactorEnabled").value(false))
                .andExpect(jsonPath("$.limitWithSecondFactor").value(8000));

        assertThat(transfer(token, "VIN001", "VIN999", "5000.00").getResponse().getStatus()).isEqualTo(201);

        MvcResult over = transfer(token, "VIN002", "VIN999", "1000.01");
        assertThat(over.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(over).get("code").asText()).isEqualTo("DAILY_LIMIT_EXCEEDED");
        assertThat(body(over).get("remaining").decimalValue()).isEqualByComparingTo("1000");

        // Between one's own accounts there is no limit, and it does not use the allowance.
        assertThat(transfer(token, "VIN002", "VIN001", "3000.00").getResponse().getStatus()).isEqualTo(201);
        assertThat(transfer(token, "VIN002", "VIN999", "1000.00").getResponse().getStatus()).isEqualTo(201);

        mvc.perform(get("/api/transfers/limits").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.usedToday").value(6000))
                .andExpect(jsonPath("$.remainingToday").value(0));
    }

    @Test
    void movementsCarryTheirCategory() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        transfer(token, "VIN001", "VIN999", "10.00");
        mvc.perform(get("/api/accounts/" + ids.accountId() + "/transactions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[0].category").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.content[1].category").value("DEPOSIT"));
    }

    @Test
    void onlyClientsHaveTransferLimits() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(get("/api/transfers/limits").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }
}
