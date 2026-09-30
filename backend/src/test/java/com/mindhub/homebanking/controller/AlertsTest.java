package com.mindhub.homebanking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindhub.homebanking.support.CapturingMailer.Mail;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Alert settings, the alerts they trigger (in-app and e-mail) and the notifications inbox. */
class AlertsTest extends IntegrationTest {

    private static final String INBOX = "/api/clients/current/notifications";
    private static final String ALERTS = "/api/clients/current/alerts";
    private static final String CHROME_ON_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36";

    private String signIn(String email) throws Exception {
        String token = accessToken(email);
        mailer.clear();
        return token;
    }

    private ResultActions setAlerts(String token, Object low, Object large, boolean login, boolean email)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("lowBalanceThreshold", low);
        body.put("largeMovementThreshold", large);
        body.put("loginAlerts", login);
        body.put("emailAlerts", email);
        return mvc.perform(put(ALERTS).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private void transfer(String token, String amount) throws Exception {
        mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceAccountNumber", "VIN001", "targetAccountNumber", "VIN999",
                                "amount", amount))))
                .andExpect(status().isCreated());
    }

    private JsonNode inbox(String token) throws Exception {
        return body(mvc.perform(get(INBOX).header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn())
                .get("content");
    }

    private List<String> types(JsonNode content) {
        return content.findValuesAsText("type");
    }

    @Test
    void defaultsAreSignInAlertsAndEmailCopiesOn() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        mvc.perform(get(ALERTS).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowBalanceThreshold").doesNotExist())
                .andExpect(jsonPath("$.largeMovementThreshold").doesNotExist())
                .andExpect(jsonPath("$.loginAlerts").value(true))
                .andExpect(jsonPath("$.emailAlerts").value(true));
    }

    @Test
    void eachSignInIsReportedWithDeviceAndIp() throws Exception {
        mvc.perform(post("/api/auth/login").header(HttpHeaders.USER_AGENT, CHROME_ON_WINDOWS)
                        .with(request -> {
                            request.setRemoteAddr("190.10.20.30");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", TestData.CLIENT_EMAIL, "password", TestData.PASSWORD))))
                .andExpect(status().isOk());

        assertThat(mailer.sent()).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).isEqualTo("Ingresaste a MindHub Brothers");
            assertThat(mail.body()).contains("Chrome en Windows").contains("190.10.20.30");
        });
        JsonNode first = inbox(signIn(TestData.CLIENT_EMAIL)).get(0);
        assertThat(first.get("type").asText()).isEqualTo("LOGIN");
        assertThat(first.get("read").asBoolean()).isFalse();
    }

    @Test
    void signInAlertsCanBeTurnedOff() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        setAlerts(melba, null, null, false, true).andExpect(status().isOk());
        String before = inbox(melba).toString();

        signIn(TestData.CLIENT_EMAIL);
        assertThat(mailer.sent()).isEmpty();
        assertThat(inbox(melba).toString()).isEqualTo(before);
    }

    @Test
    void lowBalanceAlertsOnlyWhenCrossingTheThreshold() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL); // balance 5,000.00
        setAlerts(melba, "4000", null, true, true).andExpect(status().isOk());

        transfer(melba, "500");  // 4,500: still above
        transfer(melba, "1000"); // 3,500: crosses → alert
        transfer(melba, "100");  // 3,400: already below → no repeat

        List<Mail> lowBalance = mailer.sent().stream().filter(m -> m.subject().startsWith("Saldo bajo")).toList();
        assertThat(lowBalance).singleElement()
                .satisfies(mail -> assertThat(mail.body()).contains("3.500,00").contains("4.000,00"));
        assertThat(types(inbox(melba))).containsOnlyOnce("LOW_BALANCE");
    }

    @Test
    void largeDebitsAlertWhateverTheirOrigin() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        setAlerts(melba, null, "1000", true, true).andExpect(status().isOk());

        transfer(melba, "999.99"); // below: nothing
        assertThat(types(inbox(melba))).doesNotContain("LARGE_MOVEMENT");

        transfer(melba, "1000");
        // A fixed term is a debit too: the alert does not depend on the service that moved the money.
        mvc.perform(post("/api/clients/current/fixed-terms").header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNumber", "VIN001", "amount", "1500", "termDays", 30,
                                "autoRenew", false))))
                .andExpect(status().isCreated());

        JsonNode content = inbox(melba);
        assertThat(types(content).stream().filter("LARGE_MOVEMENT"::equals)).hasSize(2);
        assertThat(content.get(0).get("link").asText()).startsWith("/movements/");
        assertThat(mailer.sent()).filteredOn(m -> m.subject().startsWith("Se debitaron")).hasSize(2);
    }

    @Test
    void withEmailOffAlertsStayInTheAppButSecurityChangesAreStillMailed() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        setAlerts(melba, null, "10", true, false).andExpect(status().isOk());

        transfer(melba, "50");
        // Melba's preference is hers only: the recipient keeps getting their e-mail.
        assertThat(mailer.sent()).extracting(Mail::to).containsExactly(TestData.OTHER_CLIENT_EMAIL);
        assertThat(types(inbox(melba))).contains("LARGE_MOVEMENT");
        mailer.clear();

        mvc.perform(post("/api/auth/password").header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", TestData.PASSWORD,
                                "newPassword", "another-long-password"))))
                .andExpect(status().isOk());
        assertThat(mailer.sent()).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Cambiaste tu contraseña"));
    }

    @Test
    void theRecipientOfATransferFindsItInTheBell() throws Exception {
        transfer(signIn(TestData.CLIENT_EMAIL), "250");

        String other = signIn(TestData.OTHER_CLIENT_EMAIL);
        JsonNode received = inbox(other).get(1); // [0] is Other's own sign-in
        assertThat(received.get("type").asText()).isEqualTo("TRANSFER_RECEIVED");
        assertThat(received.get("title").asText()).contains("250,00");
        assertThat(received.get("link").asText()).isEqualTo("/accounts/" + ids.otherAccountId());
    }

    @Test
    void notificationsCanBeCountedAndMarkedReadOnlyByTheirOwner() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        signIn(TestData.CLIENT_EMAIL); // a second sign-in alert
        mvc.perform(get(INBOX + "/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.count").value(2));

        long first = inbox(melba).get(0).get("id").asLong();
        String other = signIn(TestData.OTHER_CLIENT_EMAIL);
        mvc.perform(post(INBOX + "/" + first + "/read").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        mvc.perform(post(INBOX + "/" + first + "/read").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isNoContent());
        mvc.perform(get(INBOX + "/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.count").value(1));
        mvc.perform(post(INBOX + "/read-all").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isNoContent());
        mvc.perform(get(INBOX + "/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void accessRulesAndValidation() throws Exception {
        mvc.perform(get(INBOX)).andExpect(status().isUnauthorized());
        mvc.perform(get(ALERTS)).andExpect(status().isUnauthorized());

        String admin = signIn(TestData.ADMIN_EMAIL);
        mvc.perform(get(INBOX).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("LOGIN")); // admins get sign-in alerts too
        mvc.perform(get(ALERTS).header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isForbidden());

        String melba = signIn(TestData.CLIENT_EMAIL);
        setAlerts(melba, "-1", null, true, true).andExpect(status().isBadRequest());
        setAlerts(melba, "10.123", null, true, true).andExpect(status().isBadRequest());
        setAlerts(melba, null, "0", true, true).andExpect(status().isBadRequest());
        mvc.perform(get(INBOX).param("size", "51").header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isBadRequest());
    }
}
