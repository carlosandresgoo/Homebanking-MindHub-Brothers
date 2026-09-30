package com.mindhub.homebanking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * What a client wants to be told about. Thresholds are in ARS and {@code null} means that alert is off.
 * {@code email} copies alerts and movement notices by e-mail; security changes (password, 2FA) are
 * always e-mailed regardless.
 */
@Embeddable
public class AlertPreferences {

    /** Alert when a debit leaves an account below this balance (only when crossing it). */
    @Column(name = "alert_low_balance", precision = 19, scale = 2)
    private BigDecimal lowBalance;

    /** Alert for every debit of at least this amount. */
    @Column(name = "alert_large_movement", precision = 19, scale = 2)
    private BigDecimal largeMovement;

    @Column(name = "alert_login", nullable = false)
    private boolean login = true;

    @Column(name = "alert_email", nullable = false)
    private boolean email = true;

    public void update(BigDecimal lowBalance, BigDecimal largeMovement, boolean login, boolean email) {
        this.lowBalance = lowBalance;
        this.largeMovement = largeMovement;
        this.login = login;
        this.email = email;
    }

    public BigDecimal getLowBalance() {
        return lowBalance;
    }

    public BigDecimal getLargeMovement() {
        return largeMovement;
    }

    public boolean isLogin() {
        return login;
    }

    public boolean isEmail() {
        return email;
    }
}
