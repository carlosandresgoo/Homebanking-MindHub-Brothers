package com.mindhub.homebanking.support;

import com.mindhub.homebanking.service.notification.Mailer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test mailer: keeps sent e-mails in memory so tests can read links from them. */
public class CapturingMailer implements Mailer {

    public record Mail(String to, String subject, String body) {
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Mail(to, subject, body));
    }

    public List<Mail> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}
