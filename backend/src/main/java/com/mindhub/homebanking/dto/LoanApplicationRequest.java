package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Input for {@code POST /api/loans}: which product, how much, in how many installments, credited where. */
public record LoanApplicationRequest(
        @NotNull Long loanId,
        @NotNull @DecimalMin(value = "1.00", message = "must be at least 1.00")
        @Digits(integer = 15, fraction = 2, message = "must have at most 2 decimals") BigDecimal amount,
        @NotNull @Min(1) Integer payments,
        @NotBlank @Size(max = 20) String accountNumber) {
}
