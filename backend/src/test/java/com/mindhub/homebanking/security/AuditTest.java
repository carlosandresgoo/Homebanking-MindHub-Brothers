package com.mindhub.homebanking.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.AuditEvent;
import com.mindhub.homebanking.repository.AuditEventRepository;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditTest extends IntegrationTest {

    @Autowired
    private AuditEventRepository auditEvents;

    private List<AuditEvent> events(AuditAction action) {
        return auditEvents.findAll().stream().filter(e -> e.getAction() == action).toList();
    }

    @Test
    void loginsAreAuditedWithOutcomeAndIp() throws Exception {
        login(TestData.CLIENT_EMAIL, "wrong-password-x");
        login(TestData.CLIENT_EMAIL, TestData.PASSWORD);

        List<AuditEvent> logins = events(AuditAction.LOGIN);
        assertThat(logins).extracting(AuditEvent::getOutcome)
                .containsExactlyInAnyOrder(AuditEvent.Outcome.FAILURE, AuditEvent.Outcome.SUCCESS);
        assertThat(logins).allSatisfy(e -> {
            assertThat(e.getActor()).isEqualTo(TestData.CLIENT_EMAIL);
            assertThat(e.getIp()).isEqualTo("127.0.0.1");
        });
        assertThat(logins).filteredOn(e -> e.getOutcome() == AuditEvent.Outcome.SUCCESS)
                .singleElement().extracting(AuditEvent::getActorRole).isEqualTo("CLIENT");
    }

    @Test
    void successfulTransfersAreAuditedAndFailedOnesAreNot() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        transfer(token, "10.00");
        transfer(token, "999999.00"); // insufficient funds: rolled back

        assertThat(events(AuditAction.TRANSFER)).singleElement().satisfies(e -> {
            assertThat(e.getActor()).isEqualTo(TestData.CLIENT_EMAIL);
            assertThat(e.getTarget()).isEqualTo("VIN001 -> VIN999");
            assertThat(e.getDetails()).isEqualTo("amount=10.00");
        });
    }

    @Test
    void cardEventsNeverContainTheFullNumber() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        String number = body(mvc.perform(post("/api/clients/current/cards")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "DEBIT", "color", "GOLD")))).andReturn()).get("number").asText();

        assertThat(events(AuditAction.CARD_ISSUED)).singleElement().satisfies(e -> {
            assertThat(e.getTarget()).isEqualTo("DEBIT GOLD ****" + number.substring(12));
            assertThat(e.getTarget() + e.getDetails()).doesNotContain(number);
        });
    }

    @Test
    void adminSearchesTheTrailWithFiltersAndPaging() throws Exception {
        login(TestData.CLIENT_EMAIL, TestData.PASSWORD);
        login(TestData.OTHER_CLIENT_EMAIL, TestData.PASSWORD);
        String admin = accessToken(TestData.ADMIN_EMAIL);

        JsonNode page = body(mvc.perform(get("/api/admin/audit").param("actor", "melba").param("action", "LOGIN")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isOk()).andReturn());
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("actor").asText()).isEqualTo(TestData.CLIENT_EMAIL);

        JsonNode firstPage = body(mvc.perform(get("/api/admin/audit").param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andReturn());
        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asInt()).isGreaterThanOrEqualTo(3);

        mvc.perform(get("/api/admin/audit").param("action", "NOT_AN_ACTION")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsCanReadTheTrail() throws Exception {
        String client = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/api/admin/audit").header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit")).andExpect(status().isUnauthorized());
    }

    private void transfer(String token, String amount) throws Exception {
        mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sourceAccountNumber", "VIN001", "targetAccountNumber", "VIN999",
                        "amount", amount))));
    }
}
