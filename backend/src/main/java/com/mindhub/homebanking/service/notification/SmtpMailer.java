package com.mindhub.homebanking.service.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Real delivery through the SMTP server in {@code spring.mail.*} (MAIL_HOST and friends). Active in any
 * profile as soon as MAIL_HOST is set; otherwise {@link LoggingMailer} (dev/test) or
 * {@link UnconfiguredMailer} (prod) take its place.
 */
@Component
@ConditionalOnExpression("!'${spring.mail.host:}'.isBlank()")
class SmtpMailer implements Mailer {

    private final JavaMailSender sender;
    private final String from;

    SmtpMailer(JavaMailSender sender, @Value("${app.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    /** @throws org.springframework.mail.MailException when the server rejects or cannot be reached */
    @Override
    public void send(String to, String subject, String body) {
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
        } catch (MessagingException e) {
            throw new MailPreparationException("Could not build the e-mail", e);
        }
        sender.send(message);
    }
}
