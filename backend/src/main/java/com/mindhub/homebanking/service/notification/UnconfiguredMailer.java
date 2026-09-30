package com.mindhub.homebanking.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Production without MAIL_HOST (see {@link SmtpMailer}): it never logs message bodies (they can
 * contain reset links), only that an e-mail could not be delivered.
 */
@Component
@Profile("!dev & !test")
@ConditionalOnExpression("'${spring.mail.host:}'.isBlank()")
class UnconfiguredMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(UnconfiguredMailer.class);

    @Override
    public void send(String to, String subject, String body) {
        log.warn("E-mail not delivered (no mail provider configured): subject='{}'", subject);
    }
}
