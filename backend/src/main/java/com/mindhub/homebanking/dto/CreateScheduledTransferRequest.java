package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.ScheduledTransfer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Input for {@code POST /api/clients/current/scheduled-transfers}.
 *
 * @param targetAccountNumber account number, CBU or alias (fixed to the account number when saved)
 * @param startDate           first occurrence, from tomorrow on
 * @param maxRuns             recurring only: stop after this many; null = until cancelled
 * @param secondFactorCode    needed (403 otherwise) when a transfer of this amount to others would need it
 */
public record CreateScheduledTransferRequest(
        @NotBlank @Size(max = 20) String sourceAccountNumber,
        @NotBlank @Size(max = 24) String targetAccountNumber,
        @NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "must have at most 2 decimals") BigDecimal amount,
        @Size(max = 100) String description,
        @NotNull ScheduledTransfer.Frequency frequency,
        @NotNull LocalDate startDate,
        @Min(1) @Max(120) Integer maxRuns,
        @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String secondFactorCode) {
}
