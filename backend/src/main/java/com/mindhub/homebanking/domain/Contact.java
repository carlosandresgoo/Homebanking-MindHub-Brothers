package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A saved recipient: another client's account, with an alias chosen by the owner. */
@Entity
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(nullable = false, length = 40)
    private String alias;

    @Column(nullable = false, length = 20)
    private String accountNumber;

    /** Masked holder name ("Lucía P."): enough to recognise the recipient, not their full identity. */
    @Column(nullable = false, length = 60)
    private String holderDisplay;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** When the owner confirmed it with a 2FA code; null = not trusted. */
    private LocalDateTime trustedAt;

    protected Contact() {
    }

    public Contact(Client client, String alias, String accountNumber, String holderDisplay, LocalDateTime createdAt) {
        this.client = client;
        this.alias = alias;
        this.accountNumber = accountNumber;
        this.holderDisplay = holderDisplay;
        this.createdAt = createdAt;
    }

    public void rename(String alias) {
        this.alias = alias;
    }

    public void trust(LocalDateTime now) {
        this.trustedAt = now;
    }

    public void untrust() {
        this.trustedAt = null;
    }

    public boolean isTrusted() {
        return trustedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    public String getAlias() {
        return alias;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getHolderDisplay() {
        return holderDisplay;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Contact{id=" + id + '}';
    }
}
