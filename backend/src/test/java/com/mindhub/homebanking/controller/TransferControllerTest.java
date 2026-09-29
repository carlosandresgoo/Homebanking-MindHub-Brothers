package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferControllerTest extends IntegrationTest {

    private MvcResult transfer(String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andReturn();
    }

    private static Map<String, Object> body(String from, String to, Object amount, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceAccountNumber", from);
        body.put("targetAccountNumber", to);
        body.put("amount", amount);
        body.put("description", description);
        return body;
    }

    @Test
    void transferMovesMoneyAndRecordsBothMovements() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        MvcResult result = transfer(melba, body("vin001", "VIN999", "1234.50", "Cena"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).get("sourceBalanceAfter").decimalValue()).isEqualByComparingTo("3765.50");
        assertThat(body(result).get("description").asText()).isEqualTo("Transferencia a VIN999 · Cena");
        assertThat(body(result).has("targetBalanceAfter")).isFalse();

        mvc.perform(get("/api/accounts/" + ids.accountId()).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.balance").value(3765.50))
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.transactions[0].amount").value(1234.50));

        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        mvc.perform(get("/api/accounts/" + ids.otherAccountId()).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.balance").value(1235.50))
                .andExpect(jsonPath("$.transactions[0].type").value("CREDIT"))
                .andExpect(jsonPath("$.transactions[0].description").value("Transferencia de VIN001 · Cena"));
    }

    @Test
    void cannotSpendFromAnotherClientsAccount() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        // Same 404 as a non-existent account: the victim's account is not confirmed.
        assertThat(transfer(melba, body("VIN999", "VIN001", 1, null)).getResponse().getStatus()).isEqualTo(404);
        assertThat(transfer(melba, body("VIN-00000000", "VIN999", 1, null)).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void unknownDestinationIs404() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        assertThat(transfer(melba, body("VIN001", "VIN-00000000", 1, null)).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void insufficientFundsAndSameAccountAre422AndChangeNothing() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        MvcResult tooMuch = transfer(melba, body("VIN001", "VIN999", "5000.01", null));
        assertThat(tooMuch.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(tooMuch).get("detail").asText()).isEqualTo("Insufficient funds");

        assertThat(transfer(melba, body("VIN001", "vin001", 10, null)).getResponse().getStatus()).isEqualTo(422);

        mvc.perform(get("/api/accounts/" + ids.accountId()).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.balance").value(5000.00))
                .andExpect(jsonPath("$.transactions", hasSize(1)));
    }

    @Test
    void wholeBalanceCanBeTransferred() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        MvcResult result = transfer(melba, body("VIN001", "VIN999", "5000.00", null));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).get("sourceBalanceAfter").decimalValue()).isZero();
    }

    @Test
    void invalidAmountsAre400() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        for (Object amount : new Object[] {0, -5, "0.001", null}) {
            MvcResult result = transfer(melba, body("VIN001", "VIN999", amount, null));
            assertThat(result.getResponse().getStatus()).as("amount %s", amount).isEqualTo(400);
            assertThat(body(result).get("errors").has("amount")).isTrue();
        }
        MvcResult missing = transfer(melba, body("", "", 10, null));
        assertThat(body(missing).get("errors").has("sourceAccountNumber")).isTrue();
        assertThat(body(missing).get("errors").has("targetAccountNumber")).isTrue();
    }

    @Test
    void cannotTransferFromAClosedAccountOrToOne() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        String created = mvc.perform(post("/api/clients/current/accounts").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andReturn().getResponse().getContentAsString();
        String emptyNumber = objectMapper.readTree(created).get("number").asText();
        long emptyId = objectMapper.readTree(created).get("id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/accounts/" + emptyId).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isNoContent());

        assertThat(transfer(melba, body("VIN001", emptyNumber, 1, null)).getResponse().getStatus()).isEqualTo(404);
        assertThat(transfer(melba, body(emptyNumber, "VIN001", 1, null)).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void transfersAreForClientsOnly() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        assertThat(transfer(admin, body("VIN001", "VIN999", 1, null)).getResponse().getStatus()).isEqualTo(403);
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(json(body("VIN001", "VIN999", 1, null))))
                .andExpect(status().isUnauthorized());
    }
}
