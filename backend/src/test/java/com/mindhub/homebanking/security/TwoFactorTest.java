package com.mindhub.homebanking.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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
 * Codes are computed from the real clock. Each test uses the code of the current step to enable 2FA
 * and at most the next step's code afterwards: both are accepted however the steps roll over.
 */
class TwoFactorTest extends IntegrationTest {

    @Autowired
    private ClientRepository clients;

    /** Secret and the step whose code enabled 2FA (the next usable code is step + 1). */
    private record Enrollment(byte[] secret, long step) {
        String nextCode() {
            return Totp.code(secret, step + 1);
        }
    }

    private Enrollment enroll(String token) throws Exception {
        MvcResult setup = mvc.perform(post("/api/clients/current/2fa/setup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(setup.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        JsonNode body = body(setup);
        byte[] secret = Totp.fromBase32(body.get("secret").asText());
        assertThat(body.get("otpauthUri").asText())
                .startsWith("otpauth://totp/")
                .contains("secret=" + body.get("secret").asText());

        long step = Totp.stepAt(Instant.now());
        post2fa(token, "enable", Map.of("code", Totp.code(secret, step)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));
        return new Enrollment(secret, step);
    }

    private org.springframework.test.web.servlet.ResultActions post2fa(String token, String action,
                                                                         Map<String, String> body) throws Exception {
        return mvc.perform(post("/api/clients/current/2fa/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private MvcResult login(String code) throws Exception {
        Map<String, String> body = new HashMap<>(Map.of("email", TestData.CLIENT_EMAIL, "password", TestData.PASSWORD));
        if (code != null) {
            body.put("secondFactorCode", code);
        }
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andReturn();
    }

    private MvcResult transfer(String token, String amount, String code) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of(
                "sourceAccountNumber", "VIN001", "targetAccountNumber", "VIN999", "amount", amount));
        if (code != null) {
            body.put("secondFactorCode", code);
        }
        return mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andReturn();
    }

    @Test
    void enrollmentStoresTheSecretEncryptedAndShowsInTheProfile() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/api/clients/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.twoFactorEnabled").value(false));

        Enrollment enrollment = enroll(token);

        String stored = clients.findByEmailIgnoreCase(TestData.CLIENT_EMAIL).orElseThrow().getTotpSecret();
        assertThat(stored).isNotBlank().doesNotContain(Totp.base32(enrollment.secret()));
        mvc.perform(get("/api/clients/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));
        // A second setup would silently replace the active secret.
        mvc.perform(post("/api/clients/current/2fa/setup").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void enablingNeedsASetupAndTheRightCode() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        post2fa(token, "enable", Map.of("code", "123456")).andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/clients/current/2fa/setup").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        String wrong = Totp.code(Totp.newSecret(), Totp.stepAt(Instant.now()));
        post2fa(token, "enable", Map.of("code", wrong))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.secondFactor").value("INVALID"));
        post2fa(token, "enable", Map.of("code", "12ab")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/clients/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.twoFactorEnabled").value(false));
    }

    @Test
    void loginAsksForTheCodeAndEachCodeWorksOnce() throws Exception {
        Enrollment enrollment = enroll(accessToken(TestData.CLIENT_EMAIL));

        MvcResult withoutCode = login(null);
        assertThat(withoutCode.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(withoutCode).get("secondFactor").asText()).isEqualTo("REQUIRED");
        assertThat(withoutCode.getResponse().getCookie("refresh_token")).isNull();

        String code = enrollment.nextCode();
        MvcResult ok = login(code);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(ok).get("accessToken").asText()).isNotBlank();

        MvcResult replay = login(code);
        assertThat(replay.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(replay).get("secondFactor").asText()).isEqualTo("INVALID");
    }

    @Test
    void wrongPasswordIsStill401EvenWith2fa() throws Exception {
        enroll(accessToken(TestData.CLIENT_EMAIL));
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", TestData.CLIENT_EMAIL, "password", "wrong-password"))))
                .andReturn();
        // The code is only asked for after a correct password.
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void largeTransfersToOthersNeedACode() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        Enrollment enrollment = enroll(token);

        // Below the threshold (2,000 in tests): no code needed.
        assertThat(transfer(token, "100.00", null).getResponse().getStatus()).isEqualTo(201);

        MvcResult required = transfer(token, "2000.00", null);
        assertThat(required.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(required).get("secondFactor").asText()).isEqualTo("REQUIRED");
        mvc.perform(get("/api/accounts/" + ids.accountId()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.balance").value(4900.00));

        assertThat(transfer(token, "2000.00", enrollment.nextCode()).getResponse().getStatus()).isEqualTo(201);
        mvc.perform(get("/api/accounts/" + ids.accountId()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.balance").value(2900.00));
    }

    @Test
    void disablingNeedsPasswordAndCode() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        Enrollment enrollment = enroll(token);
        String code = enrollment.nextCode();

        post2fa(token, "disable", Map.of("password", "wrong-password", "code", code))
                .andExpect(status().isUnprocessableEntity());
        post2fa(token, "disable", Map.of("password", TestData.PASSWORD, "code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(false));
        assertThat(login(null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void onlyAnAdminCanReset2fa() throws Exception {
        String client = accessToken(TestData.CLIENT_EMAIL);
        enroll(client);

        mvc.perform(delete("/api/clients/" + ids.clientId() + "/2fa").header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isForbidden());
        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(delete("/api/clients/" + ids.clientId() + "/2fa").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(false));
        assertThat(login(null).getResponse().getStatus()).isEqualTo(200);
    }
}
