package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Input for {@code POST /api/transfers}. Account numbers are case-insensitive.
 *
 * @param secondFactorCode authenticator code; required (403 otherwise) for large transfers to other
 *                         clients when the caller has 2FA enabled
 */
public record TransferRequest(
        @NotBlank @Size(max = 20) String sourceAccountNumber,
        @NotBlank @Size(max = 20) String targetAccountNumber,
        @NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "must have at most 2 decimals") BigDecimal amount,
        @Size(max = 100) String description,
        @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String secondFactorCode) {

    public TransferRequest(String sourceAccountNumber, String targetAccountNumber, BigDecimal amount,
                           String description) {
        this(sourceAccountNumber, targetAccountNumber, amount, description, null);
    }
}
