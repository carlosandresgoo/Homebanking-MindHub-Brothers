package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * A loan granted to a client. It is repaid in equal installments of {@code totalDue / payments}
 * (rounded to cents); the last installment settles whatever remains, so the total is exact.
 */
@Entity
public class ClientLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id")
    private Loan loan;

    /** Principal credited to the client. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Principal plus interest. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDue;

    @Column(nullable = false)
    private int payments;

    @Column(nullable = false)
    private int paymentsMade;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal outstanding;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ClientLoan() {
    }

    public ClientLoan(Client client, Loan loan, BigDecimal amount, int payments, LocalDateTime createdAt) {
        this.client = client;
        this.loan = loan;
        this.amount = amount;
        this.payments = payments;
        this.totalDue = amount.add(amount.multiply(loan.getInterestRate())).setScale(2, RoundingMode.HALF_UP);
        this.outstanding = totalDue;
        this.createdAt = createdAt;
    }

    public boolean isPaidOff() {
        return paymentsMade >= payments;
    }

    /** Amount of the next installment (the last one covers any rounding remainder). */
    public BigDecimal nextInstallment() {
        if (isPaidOff()) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (paymentsMade == payments - 1) {
            return outstanding;
        }
        return totalDue.divide(BigDecimal.valueOf(payments), 2, RoundingMode.HALF_UP);
    }

    /** Registers the next installment and returns the amount paid. */
    public BigDecimal payInstallment() {
        if (isPaidOff()) {
            throw new IllegalStateException("Loan already paid off");
        }
        BigDecimal installment = nextInstallment();
        outstanding = outstanding.subtract(installment);
        paymentsMade++;
        return installment;
    }

    public Long getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    public Loan getLoan() {
        return loan;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }

    public int getPayments() {
        return payments;
    }

    public int getPaymentsMade() {
        return paymentsMade;
    }

    public BigDecimal getOutstanding() {
        return outstanding;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "ClientLoan{id=" + id + ", paymentsMade=" + paymentsMade + '/' + payments + '}';
    }
}
