package com.mindhub.homebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of a transfer, from the sender's point of view. It never includes the destination's balance
 * or owner, so account numbers of other clients reveal nothing about them.
 */
public record TransferReceiptDTO(
        Long transactionId, Long sourceAccountId, String sourceAccountNumber, String targetAccountNumber,
        BigDecimal amount, String description, LocalDateTime date, BigDecimal sourceBalanceAfter) {
}
