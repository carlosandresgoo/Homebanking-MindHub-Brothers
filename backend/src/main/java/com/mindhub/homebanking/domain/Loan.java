package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** A loan product of the catalog (reference data seeded by Flyway). */
@Entity
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal maxAmount;

    /** e.g. 0.2000 = 20% flat interest on the principal. */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal interestRate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "loan_payment", joinColumns = @JoinColumn(name = "loan_id"))
    @Column(name = "payments", nullable = false)
    @OrderBy
    private List<Integer> payments = new ArrayList<>();

    protected Loan() {
    }

    public boolean allowsPayments(int count) {
        return payments.contains(count);
    }

    public boolean allowsAmount(BigDecimal amount) {
        return amount.signum() > 0 && amount.compareTo(maxAmount) <= 0;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public List<Integer> getPayments() {
        return payments;
    }

    @Override
    public String toString() {
        return "Loan{id=" + id + ", code=" + code + '}';
    }
}
