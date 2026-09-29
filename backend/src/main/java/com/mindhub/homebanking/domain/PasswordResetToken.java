package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Single-use, short-lived password reset token; only its SHA-256 hash is stored. */
@Entity
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(String tokenHash, Client client, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.client = client;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    @Override
    public String toString() {
        return "PasswordResetToken{id=" + id + '}';
    }
}
