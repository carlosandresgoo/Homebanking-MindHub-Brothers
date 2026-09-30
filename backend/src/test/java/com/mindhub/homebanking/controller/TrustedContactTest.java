package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.security.Totp;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trusted recipients: with 2FA on, large transfers to them need no code; trusting needs one. Codes come
 * from the real clock: the current step's enables 2FA and the next step's is used once afterwards.
 */
class TrustedContactTest extends IntegrationTest {

    private static final String CONTACTS = "/api/clients/current/contacts";

    /** Enables 2FA for the token's client and returns the one code still usable afterwards. */
    private String enableTwoFactor(String token) throws Exception {
        MvcResult setup = mvc.perform(post("/api/clients/current/2fa/setup")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk()).andReturn();
        byte[] secret = Totp.fromBase32(body(setup).get("secret").asText());
        long step = Totp.stepAt(Instant.now());
        mvc.perform(post("/api/clients/current/2fa/enable").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", Totp.code(secret, step)))))
                .andExpect(status().isOk());
        return Totp.code(secret, step + 1);
    }

    private long addContact(String token) throws Exception {
        MvcResult added = mvc.perform(post(CONTACTS).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNumber", "VIN999", "alias", "Otro"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trusted").value(false))
                .andReturn();
        return body(added).get("id").asLong();
    }

    private ResultActions trust(String token, long id, String code) throws Exception {
        return mvc.perform(post(CONTACTS + "/" + id + "/trust").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", code))));
    }

    private int transfer(String token, String amount) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of(
                "sourceAccountNumber", "VIN001", "targetAccountNumber", "VIN999", "amount", amount));
        return mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void trustingNeedsTwoFactorEnabled() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        long id = addContact(melba);
        trust(melba, id, "123456")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED"));
    }

    @Test
    void aTrustedRecipientSkipsTheCodeButNotTheDailyLimit() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        String code = enableTwoFactor(melba);
        long id = addContact(melba);

        // test profile: code from 2,000; daily limit with 2FA 8,000
        assertThat(transfer(melba, "2000")).as("large and not trusted").isEqualTo(403);

        trust(melba, id, "000000").andExpect(status().isForbidden()).andExpect(jsonPath("$.secondFactor").value("INVALID"));
        mailer.clear();
        trust(melba, id, code).andExpect(status().isOk()).andExpect(jsonPath("$.trusted").value(true));
        assertThat(mailer.sent()).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Agregaste un destinatario de confianza"));

        assertThat(transfer(melba, "2000")).as("trusted: no code").isEqualTo(201);
        assertThat(transfer(melba, "7000")).as("the daily limit still applies").isEqualTo(422);

        mvc.perform(delete(CONTACTS + "/" + id + "/trust").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trusted").value(false));
        assertThat(transfer(melba, "2000")).as("no longer trusted").isEqualTo(403);
    }

    @Test
    void turningTwoFactorOffDropsTheTrust() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        String code = enableTwoFactor(melba);
        long id = addContact(melba);
        trust(melba, id, code).andExpect(status().isOk());

        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(delete("/api/clients/" + ids.clientId() + "/2fa").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());

        mvc.perform(get(CONTACTS).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$[0].trusted").value(false));
    }

    @Test
    void onlyTheOwnerCanChangeTheTrust() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        long id = addContact(melba);

        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        trust(other, id, "123456").andExpect(status().isNotFound());
        mvc.perform(delete(CONTACTS + "/" + id + "/trust").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        trust(accessToken(TestData.ADMIN_EMAIL), id, "123456").andExpect(status().isForbidden());
        mvc.perform(post(CONTACTS + "/" + id + "/trust").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", "123456")))).andExpect(status().isUnauthorized());
        trust(melba, id, "12ab").andExpect(status().isBadRequest());
    }
}
