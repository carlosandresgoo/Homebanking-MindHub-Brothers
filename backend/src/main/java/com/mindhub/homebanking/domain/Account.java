package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String number;

    /** 22 digits, see {@link Cbu}. Never changes. */
    @Column(nullable = false, unique = true, length = 22)
    private String cbu;

    /** Lower-case, see {@link AccountAlias}. The owner can change it. */
    @Column(nullable = false, unique = true, length = 20)
    private String alias;

    @Column(nullable = false)
    private LocalDateTime creationDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /** Closed accounts are kept (with their history) but hidden and unusable. */
    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    @OneToMany(mappedBy = "account")
    @OrderBy("date DESC, id DESC")
    private List<Transaction> transactions = new ArrayList<>();

    protected Account() {
    }

    public Account(String number, String cbu, String alias, LocalDateTime creationDate, BigDecimal balance) {
        this.number = number;
        this.cbu = cbu;
        this.alias = alias;
        this.creationDate = creationDate;
        this.balance = balance;
    }

    /** Adds money and records the movement. The caller persists the returned transaction. */
    public Transaction credit(BigDecimal amount, TransactionCategory category, String description,
                              LocalDateTime date) {
        requirePositive(amount);
        balance = balance.add(amount);
        return record(TransactionType.CREDIT, category, amount, description, date);
    }

    /** Removes money and records the movement. Callers must check funds first (see {@link #hasFunds}). */
    public Transaction debit(BigDecimal amount, TransactionCategory category, String description,
                             LocalDateTime date) {
        requirePositive(amount);
        if (!hasFunds(amount)) {
            throw new IllegalStateException("Insufficient funds");
        }
        balance = balance.subtract(amount);
        return record(TransactionType.DEBIT, category, amount, description, date);
    }

    public boolean hasFunds(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public boolean hasZeroBalance() {
        return balance.signum() == 0;
    }

    public void close() {
        this.active = false;
    }

    /** @param alias already normalized and checked for uniqueness by the caller */
    public void changeAlias(String alias) {
        this.alias = alias;
    }

    private Transaction record(TransactionType type, TransactionCategory category, BigDecimal amount,
                               String description, LocalDateTime date) {
        Transaction transaction = new Transaction(type, category, amount, description, date, balance, this);
        transactions.add(0, transaction);
        return transaction;
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public String getCbu() {
        return cbu;
    }

    public String getAlias() {
        return alias;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public boolean isActive() {
        return active;
    }

    public Client getClient() {
        return client;
    }

    void setClient(Client client) {
        this.client = client;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    /** Excludes the client relation and the balance (no recursion, no financial data in logs). */
    @Override
    public String toString() {
        return "Account{id=" + id + ", number='" + number + "'}";
    }
}
