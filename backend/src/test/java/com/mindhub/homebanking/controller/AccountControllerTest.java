package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest extends IntegrationTest {

    @Test
    void listsOnlyMyActiveAccounts() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/api/clients/current/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].number").value("VIN001"));
    }

    @Test
    void accountDetailAndMovementsForTheOwner() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/api/accounts/" + ids.accountId()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5000.00))
                .andExpect(jsonPath("$.transactions").doesNotExist());
        mvc.perform(get("/api/accounts/" + ids.accountId() + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(5000.00));
    }

    @Test
    void anotherClientsAccountLooksLikeItDoesNotExist() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        // 404 (not 403) so account ids of other clients cannot be probed.
        mvc.perform(get("/api/accounts/" + ids.otherAccountId()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/accounts/" + ids.otherAccountId()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanSeeAnyAccountButNotCloseIt() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(get("/api/accounts/" + ids.otherAccountId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/accounts/" + ids.otherAccountId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void opensUpToThreeActiveAccounts() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/clients/current/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.number").value(matchesPattern("VIN-\\d{8}")))
                    .andExpect(jsonPath("$.balance").value(0));
        }
        mvc.perform(post("/api/clients/current/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCannotOpenAccounts() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(post("/api/clients/current/accounts").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountWithMoneyCannotBeClosed() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(delete("/api/accounts/" + ids.accountId()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void emptyAccountIsClosedAndDisappears() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        String body = mvc.perform(post("/api/clients/current/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long newId = objectMapper.readTree(body).get("id").asLong();

        mvc.perform(delete("/api/accounts/" + newId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/accounts/" + newId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/clients/current/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void accountEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/clients/current/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts/" + ids.accountId())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/clients/current/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/accounts/" + ids.accountId())).andExpect(status().isUnauthorized());
    }
}
