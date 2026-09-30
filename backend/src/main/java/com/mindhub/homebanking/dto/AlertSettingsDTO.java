package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/**
 * A client's alerts, read and written as a whole ({@code GET/PUT /api/clients/current/alerts}).
 *
 * @param lowBalanceThreshold    alert when a debit leaves an account below it; null = off
 * @param largeMovementThreshold alert for debits of at least this amount; null = off
 * @param loginAlerts            alert on every sign-in
 * @param emailAlerts            also send alerts and movement notices by e-mail
 */
public record AlertSettingsDTO(
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @DecimalMax(value = "999999999999.99") @Digits(integer = 12, fraction = 2) BigDecimal lowBalanceThreshold,
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @DecimalMax(value = "999999999999.99") @Digits(integer = 12, fraction = 2) BigDecimal largeMovementThreshold,
        boolean loginAlerts,
        boolean emailAlerts) {
}
