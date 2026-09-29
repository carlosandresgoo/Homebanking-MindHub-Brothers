package com.mindhub.homebanking.service.notification;

/**
 * Outgoing e-mail. The dev implementation writes messages to the log; production needs a real
 * implementation (e.g. spring-boot-starter-mail with SMTP settings from the environment).
 */
public interface Mailer {

    void send(String to, String subject, String body);
}
