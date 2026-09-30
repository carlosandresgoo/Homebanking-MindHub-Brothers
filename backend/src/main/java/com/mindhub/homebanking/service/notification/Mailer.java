package com.mindhub.homebanking.service.notification;

/**
 * Outgoing e-mail (HTML body). Exactly one implementation is active: {@link SmtpMailer} when MAIL_HOST
 * is set, otherwise {@link LoggingMailer} (dev/test) or {@link UnconfiguredMailer} (prod). Services do
 * not call it directly: they go through {@link NotificationService}.
 */
public interface Mailer {

    void send(String to, String subject, String body);
}
