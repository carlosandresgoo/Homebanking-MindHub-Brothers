package com.mindhub.homebanking.security;

import com.mindhub.homebanking.support.CapturingMailer;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordTest extends IntegrationTest {

    private static final String NEW_PASSWORD = "a-brand-new-password";
    private static final Pattern TOKEN = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)");

    private MvcResult change(String token, String current, String next) throws Exception {
        return mvc.perform(post("/api/auth/password").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", current, "newPassword", next))))
                .andReturn();
    }

    private MvcResult forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email)))).andReturn();
    }

    private MvcResult reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token, "newPassword", password)))).andReturn();
    }

    private String tokenFromLastMail() {
        CapturingMailer.Mail mail = mailer.sent().getLast();
        Matcher matcher = TOKEN.matcher(mail.body());
        assertThat(matcher.find()).as("reset link in e-mail").isTrue();
        return matcher.group(1);
    }

    @Test
    void changingThePasswordRequiresTheCurrentOneAndSignsOutOtherSessions() throws Exception {
        MvcResult otherDevice = login(TestData.CLIENT_EMAIL, TestData.PASSWORD);
        Cookie otherDeviceRefresh = refreshCookie(otherDevice);
        String token = accessToken(TestData.CLIENT_EMAIL);

        assertThat(change(token, "wrong-current-pass", NEW_PASSWORD).getResponse().getStatus()).isEqualTo(422);
        assertThat(change(token, TestData.PASSWORD, TestData.PASSWORD).getResponse().getStatus()).isEqualTo(422);
        assertThat(change(token, TestData.PASSWORD, "short").getResponse().getStatus()).isEqualTo(400);

        MvcResult changed = change(token, TestData.PASSWORD, NEW_PASSWORD);
        assertThat(changed.getResponse().getStatus()).isEqualTo(200);
        assertThat(refreshCookie(changed)).isNotNull(); // this session continues with fresh tokens

        mvc.perform(post("/api/auth/refresh").cookie(otherDeviceRefresh)).andExpect(status().isUnauthorized());
        assertThat(login(TestData.CLIENT_EMAIL, TestData.PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(TestData.CLIENT_EMAIL, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void changingThePasswordRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "x", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotAnswersTheSameForKnownAndUnknownEmails() throws Exception {
        assertThat(forgot(TestData.CLIENT_EMAIL).getResponse().getStatus()).isEqualTo(202);
        assertThat(forgot("nobody@test.com").getResponse().getStatus()).isEqualTo(202);

        assertThat(mailer.sent()).hasSize(1);
        assertThat(mailer.sent().getFirst().to()).isEqualTo(TestData.CLIENT_EMAIL);
    }

    @Test
    void resetLinkChangesThePasswordOnceAndRevokesSessions() throws Exception {
        Cookie session = refreshCookie(login(TestData.CLIENT_EMAIL, TestData.PASSWORD));
        forgot(TestData.CLIENT_EMAIL);
        String token = tokenFromLastMail();

        assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);
        assertThat(login(TestData.CLIENT_EMAIL, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
        mvc.perform(post("/api/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());

        // Single use.
        assertThat(reset(token, "yet-another-password").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void aNewRequestInvalidatesThePreviousLink() throws Exception {
        forgot(TestData.CLIENT_EMAIL);
        String first = tokenFromLastMail();
        forgot(TestData.CLIENT_EMAIL);
        String second = tokenFromLastMail();

        assertThat(reset(first, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(400);
        assertThat(reset(second, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void forgedTokensAndWeakPasswordsAreRejected() throws Exception {
        assertThat(reset("forged-token", NEW_PASSWORD).getResponse().getStatus()).isEqualTo(400);
        forgot(TestData.CLIENT_EMAIL);
        assertThat(reset(tokenFromLastMail(), "short").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void forgotIsRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(forgot("user" + i + "@test.com").getResponse().getStatus()).isEqualTo(202);
        }
        assertThat(forgot("user6@test.com").getResponse().getStatus()).isEqualTo(429);
    }
}
