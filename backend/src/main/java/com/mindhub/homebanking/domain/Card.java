package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * A payment card. The full card number (PAN) and the CVV are never stored: only the last four digits
 * (for display) and a SHA-256 hash of the PAN (to keep numbers unique). The PAN and CVV are shown to
 * the client once, when the card is issued.
 */
@Entity
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 101)
    private String cardholder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CardType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CardColor color;

    @Column(nullable = false, length = 4)
    private String last4;

    @Column(nullable = false, unique = true, length = 64)
    private String numberHash;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate thruDate;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    protected Card() {
    }

    public Card(Client client, CardType type, CardColor color, String last4, String numberHash,
                LocalDate fromDate, LocalDate thruDate) {
        this.client = client;
        this.cardholder = client.getName() + " " + client.getLastName();
        this.type = type;
        this.color = color;
        this.last4 = last4;
        this.numberHash = numberHash;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
    }

    public boolean isExpired(LocalDate today) {
        return today.isAfter(thruDate);
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getCardholder() {
        return cardholder;
    }

    public CardType getType() {
        return type;
    }

    public CardColor getColor() {
        return color;
    }

    public String getLast4() {
        return last4;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getThruDate() {
        return thruDate;
    }

    public boolean isActive() {
        return active;
    }

    public Client getClient() {
        return client;
    }

    @Override
    public String toString() {
        return "Card{id=" + id + ", type=" + type + ", color=" + color + '}';
    }
}
