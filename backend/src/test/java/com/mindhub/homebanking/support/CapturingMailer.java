package com.mindhub.homebanking.support;

import com.mindhub.homebanking.service.notification.Mailer;
import org.springframework.mail.MailSendException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test mailer: keeps sent e-mails in memory so tests can read links from them, or simulates an outage. */
public class CapturingMailer implements Mailer {

    public record Mail(String to, String subject, String body) {
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();
    private volatile boolean down;

    @Override
    public void send(String to, String subject, String body) {
        if (down) {
            throw new MailSendException("SMTP server unreachable (simulated)");
        }
        sent.add(new Mail(to, subject, body));
    }

    /** While down, every send fails like an unreachable SMTP server. Reset by {@link #clear()}. */
    public void down(boolean down) {
        this.down = down;
    }

    public List<Mail> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
        down = false;
    }
}
