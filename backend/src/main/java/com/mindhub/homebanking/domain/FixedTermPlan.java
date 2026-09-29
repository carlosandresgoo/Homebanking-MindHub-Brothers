package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** An available term with its annual nominal rate (reference data, seeded by Flyway). */
@Entity
public class FixedTermPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int termDays;

    /** TNA as a fraction: 0.3500 = 35 %. */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal annualRate;

    protected FixedTermPlan() {
    }

    public Long getId() {
        return id;
    }

    public int getTermDays() {
        return termDays;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }
}
