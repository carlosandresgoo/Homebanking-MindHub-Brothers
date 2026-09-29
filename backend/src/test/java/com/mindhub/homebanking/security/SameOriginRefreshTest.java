package com.mindhub.homebanking.security;

import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Production-like setup: front and API share an origin behind nginx, so no CORS origins are configured. */
@TestPropertySource(properties = "app.security.cors.allowed-origins=")
class SameOriginRefreshTest extends IntegrationTest {

    @Test
    void refreshAcceptsOnlyTheServersOwnOrigin() throws Exception {
        Cookie cookie = refreshCookie(login(TestData.CLIENT_EMAIL, TestData.PASSWORD));

        mvc.perform(post("/api/auth/refresh").cookie(cookie).header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(cookie).header(HttpHeaders.ORIGIN, "http://localhost"))
                .andExpect(status().isOk());
    }
}
