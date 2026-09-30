package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** An entry of a client's in-app inbox (the bell). Created only by {@code NotificationService}. */
@Entity
public class Notification {

    public enum Type {
        WELCOME,
        TRANSFER_RECEIVED,
        FIXED_TERM_PAID,
        LOW_BALANCE,
        LARGE_MOVEMENT,
        LOGIN,
        PASSWORD_CHANGED,
        TWO_FACTOR_ENABLED,
        TWO_FACTOR_DISABLED,
        CONTACT_TRUSTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    /** Route of the app to open, e.g. {@code /accounts/5}. */
    @Column(length = 200)
    private String link;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime readAt;

    protected Notification() {
    }

    public Notification(Client client, Type type, String title, String message, String link,
                        LocalDateTime createdAt) {
        this.client = client;
        this.type = type;
        this.title = title;
        this.message = message;
        this.link = link;
        this.createdAt = createdAt;
    }

    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    public Type getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    /** No client relation and no message (it can contain amounts). */
    @Override
    public String toString() {
        return "Notification{id=" + id + ", type=" + type + "}";
    }
}
