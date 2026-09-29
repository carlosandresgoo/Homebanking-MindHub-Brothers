package com.mindhub.homebanking.security;

import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountLockoutTest extends IntegrationTest {

    /** Each attempt from a new IP, so the per-IP rate limit does not interfere with the per-account lockout. */
    private final AtomicInteger ip = new AtomicInteger(1);

    private MvcResult loginFromNewIp(String email, String password) throws Exception {
        String address = "10.1.0." + ip.getAndIncrement();
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(address);
                            return request;
                        })
                        .content(json(Map.of("email", email, "password", password))))
                .andReturn();
    }

    private MvcResult setStatus(String adminToken, long clientId, boolean enabled) throws Exception {
        return mvc.perform(patch("/api/clients/" + clientId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", enabled))))
                .andReturn();
    }

    @Test
    void fiveFailedLoginsLockTheAccountEvenForTheRightPasswordFromAnotherIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(loginFromNewIp(TestData.CLIENT_EMAIL, "wrong-password-x").getResponse().getStatus())
                    .isEqualTo(401);
        }

        MvcResult locked = loginFromNewIp(TestData.CLIENT_EMAIL, TestData.PASSWORD);
        assertThat(locked.getResponse().getStatus()).isEqualTo(423);
        assertThat(body(locked).get("reason").asText()).isEqualTo("LOCKED");
        assertThat(body(locked).get("lockedUntil").asText()).isNotBlank();
        // Other clients are not affected.
        assertThat(loginFromNewIp(TestData.OTHER_CLIENT_EMAIL, TestData.PASSWORD).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void aSuccessfulLoginResetsTheCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            loginFromNewIp(TestData.CLIENT_EMAIL, "wrong-password-x");
        }
        assertThat(loginFromNewIp(TestData.CLIENT_EMAIL, TestData.PASSWORD).getResponse().getStatus()).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            loginFromNewIp(TestData.CLIENT_EMAIL, "wrong-password-x");
        }
        assertThat(loginFromNewIp(TestData.CLIENT_EMAIL, TestData.PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void adminBlockEndsSessionsAndPreventsLoginUntilUnblocked() throws Exception {
        Cookie session = refreshCookie(login(TestData.CLIENT_EMAIL, TestData.PASSWORD));
        String admin = accessToken(TestData.ADMIN_EMAIL);

        MvcResult blocked = setStatus(admin, ids.clientId(), false);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(blocked).get("enabled").asBoolean()).isFalse();

        mvc.perform(post("/api/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());
        MvcResult refused = loginFromNewIp(TestData.CLIENT_EMAIL, TestData.PASSWORD);
        assertThat(refused.getResponse().getStatus()).isEqualTo(423);
        assertThat(body(refused).get("reason").asText()).isEqualTo("BLOCKED");

        mvc.perform(get("/api/clients/" + ids.clientId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(setStatus(admin, ids.clientId(), true).getResponse().getStatus()).isEqualTo(200);
        assertThat(loginFromNewIp(TestData.CLIENT_EMAIL, TestData.PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unblockingAlsoClearsATemporaryLock() throws Exception {
        for (int i = 0; i < 5; i++) {
            loginFromNewIp(TestData.CLIENT_EMAIL, "wrong-password-x");
        }
        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(get("/api/clients/" + ids.clientId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.locked").value(true));

        setStatus(admin, ids.clientId(), true);

        assertThat(loginFromNewIp(TestData.CLIENT_EMAIL, TestData.PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void onlyAdminsCanBlockAndAdminsCannotBeBlocked() throws Exception {
        String client = accessToken(TestData.CLIENT_EMAIL);
        assertThat(setStatus(client, ids.otherClientId(), false).getResponse().getStatus()).isEqualTo(403);

        String admin = accessToken(TestData.ADMIN_EMAIL);
        String adminId = body(mvc.perform(get("/api/clients/current")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andReturn()).get("id").asText();
        assertThat(setStatus(admin, Long.parseLong(adminId), false).getResponse().getStatus()).isEqualTo(422);
        assertThat(setStatus(admin, 999_999, false).getResponse().getStatus()).isEqualTo(404);
    }
}
