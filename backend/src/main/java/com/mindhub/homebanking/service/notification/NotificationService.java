package com.mindhub.homebanking.service.notification;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Contact;
import com.mindhub.homebanking.domain.FixedTerm;
import com.mindhub.homebanking.domain.Notification;
import com.mindhub.homebanking.domain.ScheduledTransfer;
import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.repository.NotificationRepository;
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
 * Tells clients what happened, through two channels:
 * <ul>
 *   <li>the in-app inbox (the bell), saved in the caller's transaction, so it exists only if the
 *       operation commits;</li>
 *   <li>e-mail, rendered now but delivered after commit: a rolled-back operation never announces itself
 *       and a delivery failure never undoes a committed one.</li>
 * </ul>
 * Security changes (password, 2FA) and the welcome are always e-mailed; movement notices and alerts
 * only when the client keeps e-mail alerts on ({@link com.mindhub.homebanking.domain.AlertPreferences}).
 * Templates live in {@code resources/templates/mail} and share {@code layout.html}.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Locale ES_AR = Locale.of("es", "AR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", ES_AR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ES_AR);

    private enum Email { ALWAYS, IF_WANTED }

    private final Mailer mailer;
    private final TemplateEngine templates;
    private final NotificationRepository inbox;
    private final Clock clock;
    private final String frontendUrl;

    public NotificationService(Mailer mailer, TemplateEngine templates, NotificationRepository inbox, Clock clock,
                               @Value("${app.frontend-url}") String frontendUrl) {
        this.mailer = mailer;
        this.templates = templates;
        this.inbox = inbox;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    public void welcome(Client client) {
        toInbox(client, Notification.Type.WELCOME, "Te damos la bienvenida",
                "Ya podés operar. Te recomendamos activar la verificación en dos pasos desde tu perfil.", "/profile");
        email(client, Email.ALWAYS, "Te damos la bienvenida a MindHub Brothers", "welcome",
                Map.of("accountsUrl", link("/accounts"), "profileUrl", link("/profile")));
    }

    public void passwordReset(Client client, String token, Duration validity) {
        email(client, Email.ALWAYS, "Restablecé tu contraseña de MindHub Brothers", "password-reset",
                Map.of("resetUrl", link("/reset-password?token=" + token), "minutes", validity.toMinutes()));
    }

    public void passwordChanged(Client client) {
        toInbox(client, Notification.Type.PASSWORD_CHANGED, "Cambiaste tu contraseña",
                "Cerramos las sesiones abiertas en otros dispositivos. ¿No fuiste vos? Comunicate con soporte.",
                "/profile");
        email(client, Email.ALWAYS, "Cambiaste tu contraseña", "password-changed",
                Map.of("when", now(), "profileUrl", link("/profile")));
    }

    public void twoFactorEnabled(Client client) {
        toInbox(client, Notification.Type.TWO_FACTOR_ENABLED, "Activaste la verificación en dos pasos",
                "Vas a necesitar el código de tu app para ingresar y para transferencias grandes.", "/profile");
        email(client, Email.ALWAYS, "Activaste la verificación en dos pasos", "two-factor-enabled",
                Map.of("when", now(), "profileUrl", link("/profile")));
    }

    /** @param byAdmin true when support turned it off (lost phone), false when the client did */
    public void twoFactorDisabled(Client client, boolean byAdmin) {
        toInbox(client, Notification.Type.TWO_FACTOR_DISABLED, "Se desactivó la verificación en dos pasos",
                byAdmin ? "Soporte la desactivó a tu pedido. Podés volver a activarla desde tu perfil."
                        : "Desactivaste la verificación en dos pasos. Podés volver a activarla desde tu perfil.",
                "/profile");
        email(client, Email.ALWAYS, "Se desactivó la verificación en dos pasos", "two-factor-disabled",
                Map.of("when", now(), "byAdmin", byAdmin, "profileUrl", link("/profile")));
    }

    /** Trusting lowers the protection of large transfers, so it is always e-mailed. */
    public void contactTrusted(Client client, Contact contact) {
        toInbox(client, Notification.Type.CONTACT_TRUSTED, contact.getAlias() + " es de confianza",
                "Las transferencias grandes a " + contact.getHolderDisplay() + " (" + contact.getAccountNumber()
                        + ") ya no te piden código. Podés quitarle la confianza desde Destinatarios.",
                "/contacts");
        email(client, Email.ALWAYS, "Agregaste un destinatario de confianza", "contact-trusted", Map.of(
                "alias", contact.getAlias(), "holder", contact.getHolderDisplay(),
                "account", contact.getAccountNumber(), "when", now(), "contactsUrl", link("/contacts")));
    }

    /** The transfer itself already notified both sides; this only leaves a note in the sender's bell. */
    public void scheduledTransferDone(ScheduledTransfer scheduled) {
        toInbox(scheduled.getClient(), Notification.Type.SCHEDULED_TRANSFER_DONE,
                "Se hizo tu transferencia programada",
                money(scheduled.getAmount()) + " a " + scheduled.getTargetHolder() + " (" + scheduled.getTargetAccountNumber()
                        + ")" + nextRunNote(scheduled), "/transfers/scheduled");
    }

    /** Always e-mailed: the money did not move and the client may need to act. */
    public void scheduledTransferFailed(ScheduledTransfer scheduled) {
        toInbox(scheduled.getClient(), Notification.Type.SCHEDULED_TRANSFER_FAILED,
                "No se pudo hacer tu transferencia programada",
                money(scheduled.getAmount()) + " a " + scheduled.getTargetHolder() + ": " + scheduled.getLastError()
                        + "." + nextRunNote(scheduled), "/transfers/scheduled");
        Map<String, Object> model = new HashMap<>();
        model.put("amount", money(scheduled.getAmount()));
        model.put("holder", scheduled.getTargetHolder());
        model.put("account", scheduled.getTargetAccountNumber());
        model.put("reason", scheduled.getLastError());
        model.put("nextRun", scheduled.getNextRun() == null ? null : date(scheduled.getNextRun()));
        model.put("scheduledUrl", link("/transfers/scheduled"));
        email(scheduled.getClient(), Email.ALWAYS, "No se pudo hacer tu transferencia programada",
                "scheduled-transfer-failed", model);
    }

    private static String nextRunNote(ScheduledTransfer scheduled) {
        return scheduled.getNextRun() == null ? "" : " Próxima: " + date(scheduled.getNextRun()) + ".";
    }

    /** Only for transfers between different clients: an e-mail to each side, the bell for the recipient. */
    public void transfer(Client sender, String sourceNumber, Client recipient, Account target,
                         BigDecimal amount, String note, LocalDateTime when) {
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        Map<String, Object> common = new HashMap<>();
        common.put("amount", money(amount));
        common.put("note", cleanNote);
        common.put("when", when.format(DATE_TIME));
        common.put("accountsUrl", link("/accounts"));

        Map<String, Object> sent = new HashMap<>(common);
        sent.put("account", mask(target.getNumber()));
        email(sender, Email.IF_WANTED, "Transferiste " + money(amount), "transfer-sent", sent);

        toInbox(recipient, Notification.Type.TRANSFER_RECEIVED, "Recibiste " + money(amount),
                "De la cuenta " + mask(sourceNumber) + (cleanNote == null ? "" : " · " + cleanNote),
                "/accounts/" + target.getId());
        Map<String, Object> received = new HashMap<>(common);
        received.put("account", mask(sourceNumber));
        email(recipient, Email.IF_WANTED, "Recibiste una transferencia de " + money(amount), "transfer-received",
                received);
    }

    public void fixedTermCreated(FixedTerm fixedTerm) {
        email(fixedTerm.getClient(), Email.IF_WANTED, "Constituiste un plazo fijo", "fixed-term-created", Map.of(
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
        toInbox(paid.getClient(), Notification.Type.FIXED_TERM_PAID, "Venció tu plazo fijo",
                "Se acreditaron " + money(paid.getTotal()) + " en la cuenta " + paid.getAccount().getNumber()
                        + (renewed == null ? "." : " y se reinvirtieron hasta el " + date(renewed.getMaturityDate()) + "."),
                "/investments");
        Map<String, Object> model = new HashMap<>();
        model.put("principal", money(paid.getPrincipal()));
        model.put("interest", money(paid.getInterest()));
        model.put("total", money(paid.getTotal()));
        model.put("account", paid.getAccount().getNumber());
        model.put("renewedMaturity", renewed == null ? null : date(renewed.getMaturityDate()));
        model.put("investmentsUrl", link("/investments"));
        email(paid.getClient(), Email.IF_WANTED, "Tu plazo fijo venció: se acreditaron " + money(paid.getTotal()),
                "fixed-term-paid", model);
    }

    /** A debit left the account at {@code balanceAfter}, below the client's threshold. */
    public void lowBalance(Client client, Account account, BigDecimal balanceAfter, BigDecimal threshold) {
        String balance = money(balanceAfter);
        toInbox(client, Notification.Type.LOW_BALANCE, "Saldo bajo en " + account.getNumber(),
                "Te quedan " + balance + ", menos de los " + money(threshold) + " que elegiste como aviso.",
                "/accounts/" + account.getId());
        email(client, Email.IF_WANTED, "Saldo bajo: te quedan " + balance, "low-balance", Map.of(
                "account", account.getNumber(), "balance", balance, "threshold", money(threshold),
                "accountUrl", link("/accounts/" + account.getId()), "profileUrl", link("/profile")));
    }

    /** A debit of at least the client's threshold. */
    public void largeMovement(Client client, Transaction debit, BigDecimal threshold) {
        Account account = debit.getAccount();
        String amount = money(debit.getAmount());
        toInbox(client, Notification.Type.LARGE_MOVEMENT, "Débito de " + amount,
                debit.getDescription() + " · cuenta " + account.getNumber(), "/movements/" + debit.getId());
        email(client, Email.IF_WANTED, "Se debitaron " + amount + " de tu cuenta", "large-movement", Map.of(
                "amount", amount, "description", debit.getDescription(), "account", account.getNumber(),
                "when", debit.getDate().format(DATE_TIME), "threshold", money(threshold),
                "receiptUrl", link("/movements/" + debit.getId()), "profileUrl", link("/profile")));
    }

    /** A successful sign-in, when the client keeps sign-in alerts on. */
    public void login(Client client, String device, String ip) {
        String when = now();
        toInbox(client, Notification.Type.LOGIN, "Nuevo ingreso a tu cuenta",
                "Desde " + device + " (IP " + ip + ") el " + when + ". ¿No fuiste vos? Cambiá tu contraseña.",
                "/profile");
        email(client, Email.IF_WANTED, "Ingresaste a MindHub Brothers", "login",
                Map.of("device", device, "ip", ip, "when", when, "profileUrl", link("/profile")));
    }

    private void toInbox(Client client, Notification.Type type, String title, String message, String link) {
        inbox.save(new Notification(client, type, title, message, link, LocalDateTime.now(clock)));
    }

    private void email(Client client, Email policy, String subject, String template, Map<String, Object> model) {
        if (policy == Email.IF_WANTED && !client.getAlerts().isEmail()) {
            return;
        }
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
