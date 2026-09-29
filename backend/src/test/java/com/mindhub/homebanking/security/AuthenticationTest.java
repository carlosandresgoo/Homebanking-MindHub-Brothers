package com.mindhub.homebanking.security;

import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationTest extends IntegrationTest {

    @Test
    void loginReturnsShortLivedBearerAndHardenedRefreshCookie() throws Exception {
        MvcResult result = login(TestData.CLIENT_EMAIL, TestData.PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body(result).get("expiresIn").asLong()).isEqualTo(900);
        assertThat(body(result).get("role").asText()).isEqualTo("CLIENT");
        assertThat(body(result).has("refreshToken")).isFalse();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains("refresh_token=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/auth");
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        assertThat(login("MELBA@test.com", TestData.PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameGeneric401() throws Exception {
        MvcResult wrongPassword = login(TestData.CLIENT_EMAIL, "wrong-password-123");
        MvcResult unknownUser = login("nobody@test.com", TestData.PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(wrongPassword).get("detail").asText()).isEqualTo("Authentication failed");
        assertThat(body(unknownUser).get("detail")).isEqualTo(body(wrongPassword).get("detail"));
        assertThat(wrongPassword.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void loginValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginIsRateLimitedPerClientIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login(TestData.CLIENT_EMAIL, "wrong-password-123").getResponse().getStatus()).isEqualTo(401);
        }
        // Even the right password is refused once the bucket is empty: brute force cannot continue.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", TestData.CLIENT_EMAIL, "password", TestData.PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // A different client IP has its own bucket (another user: Melba's account is now locked, see
        // AccountLockoutTest).
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.2");
                            return request;
                        })
                        .content(json(Map.of("email", TestData.OTHER_CLIENT_EMAIL, "password", TestData.PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTheTokenAndIssuesANewAccessToken() throws Exception {
        Cookie original = refreshCookie(login(TestData.CLIENT_EMAIL, TestData.PASSWORD));

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(original))
                .andExpect(status().isOk())
                .andReturn();

        Cookie rotated = refreshCookie(refreshed);
        assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(original.getValue());
        String newAccessToken = body(refreshed).get("accessToken").asText();
        mvc.perform(get("/api/clients/current").header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeSession() throws Exception {
        Cookie original = refreshCookie(login(TestData.CLIENT_EMAIL, TestData.PASSWORD));
        Cookie rotated = refreshCookie(mvc.perform(post("/api/auth/refresh").cookie(original))
                .andExpect(status().isOk()).andReturn());

        // Attacker replays the stolen, already-used token.
        mvc.perform(post("/api/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());
        // The legitimate holder's newer token is revoked as well.
        mvc.perform(post("/api/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieOrWithUnknownTokenFails() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "forged")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshFromUntrustedOriginIsRejected() throws Exception {
        Cookie cookie = refreshCookie(login(TestData.CLIENT_EMAIL, TestData.PASSWORD));

        // Rejected by the CORS filter first (403); AuthController's Origin check is the second layer for
        // same-origin deployments where no CORS origins are configured.
        mvc.perform(post("/api/auth/refresh").cookie(cookie).header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").cookie(cookie).header(HttpHeaders.ORIGIN, "http://localhost:4200"))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
        Cookie cookie = refreshCookie(login(TestData.CLIENT_EMAIL, TestData.PASSWORD));

        mvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }
}
