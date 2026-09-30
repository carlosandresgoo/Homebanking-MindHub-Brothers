package com.mindhub.homebanking.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/** Production: sends real e-mails via SMTP (configured via spring.mail.* properties). */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
class SmtpMailer implements Mailer {

	private static final Logger log = LoggerFactory.getLogger(SmtpMailer.class);

	private final JavaMailSender mailSender;
	private final String from;

	SmtpMailer(JavaMailSender mailSender) {
		this.mailSender = mailSender;
		this.from = "noreply@mindhubbrothers.com";
	}

	@Override
	public void send(String to, String subject, String body) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");
			helper.setFrom(from);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(body, true);  // true = HTML
			mailSender.send(message);
			log.debug("Email sent to {} with subject '{}'", to, subject);
		} catch (MessagingException e) {
			log.error("Failed to send email to {}", to, e);
			// En producción, podrías reintentar con una cola (RabbitMQ, Kafka, etc.)
			throw new RuntimeException("Email delivery failed", e);
		}
	}
}
