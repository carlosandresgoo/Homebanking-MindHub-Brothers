package com.mindhub.homebanking.service.notification;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.FixedTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Transactional e-mails to clients. The body is rendered right away, inside the caller's transaction
 * (so lazy relations can still be read), but it is delivered only after that transaction commits: a
 * rolled-back operation never announces itself, and a delivery failure never undoes a committed one.
 * Templates live in {@code resources/templates/mail} and share {@code layout.html}.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Locale ES_AR = Locale.of("es", "AR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", ES_AR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ES_AR);

    private final Mailer mailer;
    private final TemplateEngine templates;
    private final Clock clock;
    private final String frontendUrl;

    public NotificationService(Mailer mailer, TemplateEngine templates, Clock clock,
                               @Value("${app.frontend-url}") String frontendUrl) {
        this.mailer = mailer;
        this.templates = templates;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    public void welcome(Client client) {
        send(client, "Te damos la bienvenida a MindHub Brothers", "welcome",
                Map.of("accountsUrl", link("/accounts"), "profileUrl", link("/profile")));
    }

    public void passwordReset(Client client, String token, Duration validity) {
        send(client, "Restablecé tu contraseña de MindHub Brothers", "password-reset",
                Map.of("resetUrl", link("/reset-password?token=" + token), "minutes", validity.toMinutes()));
    }

    public void passwordChanged(Client client) {
        send(client, "Cambiaste tu contraseña", "password-changed",
                Map.of("when", now(), "profileUrl", link("/profile")));
    }

    public void twoFactorEnabled(Client client) {
        send(client, "Activaste la verificación en dos pasos", "two-factor-enabled",
                Map.of("when", now(), "profileUrl", link("/profile")));
    }

    /** @param byAdmin true when support turned it off (lost phone), false when the client did */
    public void twoFactorDisabled(Client client, boolean byAdmin) {
        send(client, "Se desactivó la verificación en dos pasos", "two-factor-disabled",
                Map.of("when", now(), "byAdmin", byAdmin, "profileUrl", link("/profile")));
    }

    /** One e-mail to each side; call it only for transfers between different clients. */
    public void transfer(Client sender, String sourceNumber, Client recipient, String targetNumber,
                         BigDecimal amount, String note, LocalDateTime when) {
        Map<String, Object> common = new HashMap<>();
        common.put("amount", money(amount));
        common.put("note", note == null || note.isBlank() ? null : note.trim());
        common.put("when", when.format(DATE_TIME));
        common.put("accountsUrl", link("/accounts"));

        Map<String, Object> sent = new HashMap<>(common);
        sent.put("account", mask(targetNumber));
        send(sender, "Transferiste " + money(amount), "transfer-sent", sent);

        Map<String, Object> received = new HashMap<>(common);
        received.put("account", mask(sourceNumber));
        send(recipient, "Recibiste una transferencia de " + money(amount), "transfer-received", received);
    }

    public void fixedTermCreated(FixedTerm fixedTerm) {
        send(fixedTerm.getClient(), "Constituiste un plazo fijo", "fixed-term-created", Map.of(
                "principal", money(fixedTerm.getPrincipal()),
                "rate", percent(fixedTerm.getAnnualRate()),
                "days", fixedTerm.getTermDays(),
                "interest", money(fixedTerm.getInterest()),
                "total", money(fixedTerm.getTotal()),
                "maturity", date(fixedTerm.getMaturityDate()),
                "account", fixedTerm.getAccount().getNumber(),
                "autoRenew", fixedTerm.isAutoRenew(),
                "investmentsUrl", link("/investments")));
    }

    /** @param renewed the new fixed term when it was reinvested automatically, otherwise null */
    public void fixedTermPaid(FixedTerm paid, FixedTerm renewed) {
        Map<String, Object> model = new HashMap<>();
        model.put("principal", money(paid.getPrincipal()));
        model.put("interest", money(paid.getInterest()));
        model.put("total", money(paid.getTotal()));
        model.put("account", paid.getAccount().getNumber());
        model.put("renewedMaturity", renewed == null ? null : date(renewed.getMaturityDate()));
        model.put("investmentsUrl", link("/investments"));
        send(paid.getClient(), "Tu plazo fijo venció: se acreditaron " + money(paid.getTotal()),
                "fixed-term-paid", model);
    }

    private void send(Client client, String subject, String template, Map<String, Object> model) {
        Context context = new Context(ES_AR);
        context.setVariables(model);
        context.setVariable("name", client.getName());
        context.setVariable("subject", subject);
        String body = templates.process(template, context);
        String to = client.getEmail();
        afterCommit(() -> deliver(to, subject, body));
    }

    private void deliver(String to, String subject, String body) {
        try {
            mailer.send(to, subject, body);
        } catch (RuntimeException e) {
            // The operation already happened; losing its e-mail must not turn it into an error.
            // No recipient in the log (personal data).
            log.warn("E-mail '{}' could not be delivered", subject, e);
        }
    }

    private static void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private String link(String path) {
        return frontendUrl + path;
    }

    private String now() {
        return LocalDateTime.now(clock).format(DATE_TIME);
    }

    private static String date(LocalDate date) {
        return date.format(DATE);
    }

    /** es-AR currency, e.g. "$ 1.234,50" (NumberFormat is not thread-safe: one per call). */
    static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(ES_AR).format(amount);
    }

    /** Stored rates are fractions (0.3500); shown as "35,00 %". */
    static String percent(BigDecimal rate) {
        NumberFormat format = NumberFormat.getPercentInstance(ES_AR);
        format.setMinimumFractionDigits(2);
        return format.format(rate);
    }

    /** Only the last four characters of the other party's account, e.g. "VIN-12345678" → "···5678". */
    static String mask(String accountNumber) {
        return accountNumber.length() <= 4 ? accountNumber : "···" + accountNumber.substring(accountNumber.length() - 4);
    }
}
