package com.mindhub.homebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A loan held by the caller, with its repayment status. */
public record ClientLoanDTO(
        Long id, Long loanId, String code, String name, BigDecimal amount, BigDecimal totalDue,
        int payments, int paymentsMade, BigDecimal nextInstallment, BigDecimal outstanding, boolean paidOff,
        LocalDateTime createdAt) {
}
