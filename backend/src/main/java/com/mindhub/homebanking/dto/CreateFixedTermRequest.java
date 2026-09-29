package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Input for {@code POST /api/clients/current/fixed-terms}. The minimum amount and the available terms
 * are business rules (422), not validation.
 */
public record CreateFixedTermRequest(
        @NotBlank @Size(max = 20) String accountNumber,
        @NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "must have at most 2 decimals") BigDecimal amount,
        @NotNull @Positive Integer termDays,
        boolean autoRenew) {
}
