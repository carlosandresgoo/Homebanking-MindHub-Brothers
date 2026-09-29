package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A fixed-term deposit. The interest is fixed when it is created (simple interest, 365-day year) and
 * paid, with the principal, to the same account on the maturity date.
 */
@Entity
public class FixedTerm {

    public enum Status { ACTIVE, PAID }

    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal annualRate;

    @Column(nullable = false)
    private int termDays;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal interest;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate maturityDate;

    @Column(nullable = false)
    private boolean autoRenew;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    private LocalDateTime paidAt;

    /** The fixed term whose payout was reinvested into this one (automatic renewal). */
    private Long renewedFrom;

    protected FixedTerm() {
    }

    public FixedTerm(Client client, Account account, BigDecimal principal, FixedTermPlan plan, LocalDate startDate,
                     boolean autoRenew, Long renewedFrom) {
        this.client = client;
        this.account = account;
        this.principal = principal;
        this.annualRate = plan.getAnnualRate();
        this.termDays = plan.getTermDays();
        this.interest = interestFor(principal, plan.getAnnualRate(), plan.getTermDays());
        this.startDate = startDate;
        this.maturityDate = startDate.plusDays(plan.getTermDays());
        this.autoRenew = autoRenew;
        this.renewedFrom = renewedFrom;
    }

    /** principal × TNA × days / 365, rounded to cents (half-even, as banks do). */
    public static BigDecimal interestFor(BigDecimal principal, BigDecimal annualRate, int days) {
        return principal.multiply(annualRate).multiply(BigDecimal.valueOf(days))
                .divide(DAYS_PER_YEAR, 2, RoundingMode.HALF_EVEN);
    }

    public boolean isDue(LocalDate today) {
        return status == Status.ACTIVE && !maturityDate.isAfter(today);
    }

    public void markPaid(LocalDateTime when) {
        this.status = Status.PAID;
        this.paidAt = when;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public BigDecimal getTotal() {
        return principal.add(interest);
    }

    public Long getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public int getTermDays() {
        return termDays;
    }

    public BigDecimal getInterest() {
        return interest;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public Long getRenewedFrom() {
        return renewedFrom;
    }

    @Override
    public String toString() {
        return "FixedTerm{id=" + id + ", status=" + status + '}';
    }
}
