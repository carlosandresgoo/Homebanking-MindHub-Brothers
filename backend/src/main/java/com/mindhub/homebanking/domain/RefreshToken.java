package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Server-side record of an issued refresh token. Only the SHA-256 hash of the token is stored,
 * so a database leak does not expose usable tokens.
 */
@Entity
public class RefreshToken {

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

    @Column(nullable = false)
    private boolean revoked;

    protected RefreshToken() {
    }

    public RefreshToken(String tokenHash, Client client, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.client = client;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Client getClient() {
        return client;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void revoke() {
        this.revoked = true;
    }

    @Override
    public String toString() {
        return "RefreshToken{id=" + id + ", revoked=" + revoked + '}';
    }
}
