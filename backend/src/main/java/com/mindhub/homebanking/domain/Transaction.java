package com.mindhub.homebanking.domain;

import jakarta.persistence.*;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A movement on an account. Created only through {@link Account#credit} / {@link Account#debit}; saving it
 * publishes {@link MovementRecorded} (alerts listen to it).
 */
@Entity
@Table(name = "account_transaction")
public class Transaction extends AbstractAggregateRoot<Transaction> {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionCategory category;

    /** For transfers: the number of the other account. */
    @Column(length = 20)
    private String counterparty;

    protected Transaction() {
    }

    Transaction(TransactionType type, TransactionCategory category, BigDecimal amount, String description,
                LocalDateTime date, BigDecimal balanceAfter, Account account) {
        this.type = type;
        this.category = category;
        this.amount = amount;
        this.description = description;
        this.date = date;
        this.balanceAfter = balanceAfter;
        this.account = account;
        registerEvent(new MovementRecorded(this));
    }

    /** Records the other account of a transfer; returns this for chaining. */
    public Transaction withCounterparty(String accountNumber) {
        this.counterparty = accountNumber;
        return this;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public String getCounterparty() {
        return counterparty;
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
