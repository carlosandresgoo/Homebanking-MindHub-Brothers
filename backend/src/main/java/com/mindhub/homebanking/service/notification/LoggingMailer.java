package com.mindhub.homebanking.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev/test without MAIL_HOST: "sends" e-mails by printing them, so links (e.g. password reset) can be
 * followed locally.
 */
@Component
@Profile({"dev", "test"})
@ConditionalOnExpression("'${spring.mail.host:}'.isBlank()")
class LoggingMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailer.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("""

                ===== E-MAIL (dev, not sent) =====
                To: {}
                Subject: {}

                {}
                ==================================""", to, subject, body);
    }
}
