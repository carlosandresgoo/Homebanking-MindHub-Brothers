package com.mindhub.homebanking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindhub.homebanking.security.Totp;
import com.mindhub.homebanking.service.ScheduledTransferService;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Scheduling, running (the job is called with a chosen "today"), failures, 2FA and ownership. */
class ScheduledTransferControllerTest extends IntegrationTest {

    private static final String URL = "/api/clients/current/scheduled-transfers";

    @Autowired
    private ScheduledTransferService service;

    @Autowired
    private Clock clock;

    private LocalDate tomorrow() {
        return LocalDate.now(clock).plusDays(1);
    }

    private Map<String, Object> body(String target, String amount, String frequency, LocalDate start) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceAccountNumber", "VIN001");
        body.put("targetAccountNumber", target);
        body.put("amount", amount);
        body.put("frequency", frequency);
        body.put("startDate", start.toString());
        return body;
    }

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private long createdId(String token, Map<String, Object> body) throws Exception {
        MvcResult result = create(token, body).andExpect(status().isCreated()).andReturn();
        return body(result).get("id").asLong();
    }

    private JsonNode mine(String token) throws Exception {
        return body(mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn());
    }

    private JsonNode balance(String token, Long accountId) throws Exception {
        return body(mvc.perform(get("/api/accounts/" + accountId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn()).get("balance");
    }

    @Test
    void schedulesByAliasAndFixesTheDestinationAccount() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        create(melba, body("vin999.test", "100", "ONCE", tomorrow()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetAccountNumber").value("VIN999"))
                .andExpect(jsonPath("$.targetHolder").value("Other C."))
                .andExpect(jsonPath("$.nextRun").value(tomorrow().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.maxRuns").value(1));
        assertThat(mine(melba)).hasSize(1);
    }

    @Test
    void validatesDatesAccountsAndInput() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        create(melba, body("VIN999", "100", "ONCE", LocalDate.now(clock)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("START_DATE"));
        create(melba, body("VIN001", "100", "ONCE", tomorrow())).andExpect(status().isUnprocessableEntity());
        create(melba, body("nadie.tiene.esto", "100", "ONCE", tomorrow())).andExpect(status().isNotFound());

        Map<String, Object> othersSource = body("VIN001", "100", "ONCE", tomorrow());
        othersSource.put("sourceAccountNumber", "VIN999");
        create(melba, othersSource).andExpect(status().isNotFound());

        create(melba, body("VIN999", "100", "YEARLY", tomorrow())).andExpect(status().isBadRequest());
        create(melba, body("VIN999", "0", "ONCE", tomorrow())).andExpect(status().isBadRequest());
        Map<String, Object> tooMany = body("VIN999", "1", "WEEKLY", tomorrow());
        tooMany.put("maxRuns", 121);
        create(melba, tooMany).andExpect(status().isBadRequest());
    }

    @Test
    void runsOnItsDateExactlyOnce() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        createdId(melba, body("VIN999", "100", "ONCE", tomorrow()));

        assertThat(service.runDue(LocalDate.now(clock))).as("not yet").isZero();
        assertThat(service.runDue(tomorrow())).isEqualTo(1);
        assertThat(service.runDue(tomorrow())).as("never twice").isZero();

        assertThat(balance(melba, ids.accountId()).decimalValue()).isEqualByComparingTo("4900.00");
        JsonNode scheduled = mine(melba).get(0);
        assertThat(scheduled.get("status").asText()).isEqualTo("FINISHED");
        assertThat(scheduled.get("lastOutcome").asText()).isEqualTo("DONE");
        mvc.perform(get("/api/accounts/" + ids.accountId() + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.content[0].description").value("Transferencia programada a VIN999"));
        mvc.perform(get("/api/clients/current/notifications").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.content[0].type").value("SCHEDULED_TRANSFER_DONE"));
    }

    @Test
    void aFailedOccurrenceIsRecordedNotifiedAndTheNextOneStaysScheduled() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        createdId(melba, body("VIN999", "5000.01", "MONTHLY", tomorrow())); // Melba has 5,000.00
        mailer.clear();

        assertThat(service.runDue(tomorrow())).isZero();

        JsonNode scheduled = mine(melba).get(0);
        assertThat(scheduled.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(scheduled.get("lastOutcome").asText()).isEqualTo("FAILED");
        assertThat(scheduled.get("lastError").asText()).isEqualTo("No había saldo suficiente en la cuenta de origen");
        assertThat(scheduled.get("runs").asInt()).isEqualTo(1);
        assertThat(scheduled.get("nextRun").asText()).isEqualTo(tomorrow().plusMonths(1).toString());
        assertThat(balance(melba, ids.accountId()).decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(mailer.sent()).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("No se pudo hacer tu transferencia programada"));
    }

    @Test
    void schedulingALargeTransferToSomeoneElseAsksForTheCodeOnceNotAtEachRun() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        MvcResult setup = mvc.perform(post("/api/clients/current/2fa/setup")
                .header(HttpHeaders.AUTHORIZATION, bearer(melba))).andReturn();
        byte[] secret = Totp.fromBase32(body(setup).get("secret").asText());
        long step = Totp.stepAt(Instant.now());
        mvc.perform(post("/api/clients/current/2fa/enable").header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", Totp.code(secret, step)))))
                .andExpect(status().isOk());

        Map<String, Object> large = body("VIN999", "2000", "ONCE", tomorrow()); // test threshold: 2,000
        create(melba, large).andExpect(status().isForbidden()).andExpect(jsonPath("$.secondFactor").value("REQUIRED"));
        large.put("secondFactorCode", Totp.code(secret, step + 1));
        create(melba, large).andExpect(status().isCreated());

        assertThat(service.runDue(tomorrow())).isEqualTo(1);
        assertThat(balance(melba, ids.accountId()).decimalValue()).isEqualByComparingTo("3000.00");
    }

    @Test
    void pausingCancellingAndMaxRuns() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        long weekly = createdId(melba, body("VIN999", "10", "WEEKLY", tomorrow()));
        mvc.perform(post(URL + "/" + weekly + "/pause").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.status").value("PAUSED"));
        assertThat(service.runDue(tomorrow())).as("paused").isZero();
        mvc.perform(post(URL + "/" + weekly + "/resume").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextRun").value(tomorrow().toString()));
        mvc.perform(delete(URL + "/" + weekly).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(service.runDue(tomorrow())).as("cancelled").isZero();

        long once = createdId(melba, body("VIN999", "10", "ONCE", tomorrow()));
        mvc.perform(post(URL + "/" + once + "/pause").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isUnprocessableEntity());

        Map<String, Object> twice = body("VIN999", "10", "WEEKLY", tomorrow().plusDays(1));
        twice.put("maxRuns", 2);
        createdId(melba, twice);
        LocalDate start = tomorrow().plusDays(1);
        service.runDue(start); // also runs the one-off (due since tomorrow)
        assertThat(service.runDue(start.plusWeeks(1))).isEqualTo(1);
        assertThat(mine(melba).get(0).get("status").asText()).isEqualTo("FINISHED");
    }

    @Test
    void onlyTheOwnerSeesAndChangesThem() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        long id = createdId(melba, body("VIN999", "10", "WEEKLY", tomorrow()));

        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        assertThat(mine(other)).isEmpty();
        mvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(post(URL + "/" + id + "/pause").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(accessToken(TestData.ADMIN_EMAIL))))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void limitsHowManyCanBeOpen() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        for (int i = 0; i < 20; i++) {
            createdId(melba, body("VIN999", "1", "MONTHLY", tomorrow()));
        }
        create(melba, body("VIN999", "1", "MONTHLY", tomorrow()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOO_MANY_SCHEDULED"));
    }
}
