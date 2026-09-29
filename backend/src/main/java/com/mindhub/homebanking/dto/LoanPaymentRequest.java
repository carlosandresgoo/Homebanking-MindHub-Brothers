package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Input for paying the next installment: the caller's account to debit. */
public record LoanPaymentRequest(@NotBlank @Size(max = 20) String accountNumber) {
}
