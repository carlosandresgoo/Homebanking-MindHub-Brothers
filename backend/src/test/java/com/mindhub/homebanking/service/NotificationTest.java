package com.mindhub.homebanking.service;

import com.mindhub.homebanking.support.CapturingMailer.Mail;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** E-mails are sent after the operation commits, in es-AR format, and never break the operation. */
class NotificationTest extends IntegrationTest {

    @Autowired
    private FixedTermService fixedTermService;

    @Autowired
    private Clock clock;

    private MvcResult transfer(String token, String amount, String description) throws Exception {
        return mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceAccountNumber", "VIN001", "targetAccountNumber", "VIN999",
                                "amount", amount, "description", description))))
                .andReturn();
    }

    /** Signs in and forgets the sign-in alert e-mail, so each test only sees the mails it causes. */
    private String signIn(String email) throws Exception {
        String token = accessToken(email);
        mailer.clear();
        return token;
    }

    private Mail mailTo(String email) {
        return mailer.sent().stream().filter(m -> m.to().equals(email)).findFirst().orElseThrow();
    }

    @Test
    void signUpSendsAWelcomeEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Chloe", "lastName", "Obrian", "email", "chloe@test.com",
                                "password", "a-long-enough-password"))))
                .andExpect(status().isCreated());

        assertThat(mailer.sent()).singleElement().satisfies(mail -> {
            assertThat(mail.to()).isEqualTo("chloe@test.com");
            assertThat(mail.subject()).contains("bienvenida");
            assertThat(mail.body()).contains("Hola <strong>Chloe</strong>").contains("http://localhost:4200/accounts");
        });
    }

    @Test
    void transferToAnotherClientNotifiesBothSidesWithMaskedAccounts() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        assertThat(transfer(melba, "1234.50", "Cena").getResponse().getStatus()).isEqualTo(201);

        assertThat(mailer.sent()).hasSize(2);
        Mail sent = mailTo(TestData.CLIENT_EMAIL);
        assertThat(sent.subject()).startsWith("Transferiste");
        assertThat(sent.body()).contains("1.234,50").contains("···N999").contains("Cena")
                .doesNotContain("VIN999"); // only the last digits of the other party's account
        Mail received = mailTo(TestData.OTHER_CLIENT_EMAIL);
        assertThat(received.subject()).startsWith("Recibiste");
        assertThat(received.body()).contains("1.234,50").contains("···N001").doesNotContain("VIN001");
    }

    @Test
    void theDescriptionCannotInjectHtmlIntoTheRecipientsEmail() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        transfer(melba, "10", "<a href=\"https://evil.example\">Reclamá tu premio</a>");

        assertThat(mailTo(TestData.OTHER_CLIENT_EMAIL).body())
                .doesNotContain("<a href=\"https://evil.example\"")
                .contains("&lt;a href=");
    }

    @Test
    void rejectedOperationsSendNothing() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        assertThat(transfer(melba, "99999", "Sin fondos").getResponse().getStatus()).isEqualTo(422);

        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void anEmailOutageDoesNotUndoTheTransfer() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        mailer.down(true);

        assertThat(transfer(melba, "1234.50", "Cena").getResponse().getStatus()).isEqualTo(201);
        mvc.perform(get("/api/accounts/" + ids.accountId()).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$.balance").value(3765.50));
        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    void fixedTermEmailsShowTheRateAsAPercentageAndThePayout() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        mvc.perform(post("/api/clients/current/fixed-terms").header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNumber", "VIN001", "amount", "1000", "termDays", 30,
                                "autoRenew", false))))
                .andExpect(status().isCreated());

        assertThat(mailer.sent()).singleElement().satisfies(mail -> assertThat(mail.body())
                .contains("35,00").contains("%") // stored as 0.3500
                .contains("1.000,00").contains("30 días"));

        mailer.clear();
        fixedTermService.payDue(LocalDate.now(clock).plusDays(30));

        assertThat(mailer.sent()).singleElement().satisfies(mail -> {
            assertThat(mail.subject()).startsWith("Tu plazo fijo venció");
            assertThat(mail.body()).contains("1.028,77").contains("ya está disponible");
        });
    }

    @Test
    void passwordChangeIsReported() throws Exception {
        String melba = signIn(TestData.CLIENT_EMAIL);
        mvc.perform(post("/api/auth/password").header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", TestData.PASSWORD,
                                "newPassword", "another-long-password"))))
                .andExpect(status().isOk());

        assertThat(mailer.sent()).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Cambiaste tu contraseña"));
    }
}
