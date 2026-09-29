package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A movement on an account. Created only through {@link Account#credit} / {@link Account#debit}. */
@Entity
@Table(name = "account_transaction")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime date;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    protected Transaction() {
    }

    Transaction(TransactionType type, BigDecimal amount, String description, LocalDateTime date,
                BigDecimal balanceAfter, Account account) {
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.date = date;
        this.balanceAfter = balanceAfter;
        this.account = account;
    }

    public Long getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Account getAccount() {
        return account;
    }

    @Override
    public String toString() {
        return "Transaction{id=" + id + ", type=" + type + '}';
    }
}
