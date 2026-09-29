package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.TransactionCategory;
import com.mindhub.homebanking.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Printable receipt of one movement.
 *
 * @param accountHolder        full name of the account's owner (the caller, or an admin viewing it)
 * @param counterparty         for transfers, the other account's number
 * @param counterpartyHolder   for transfers, the other account's owner, masked ("Lucía P.")
 */
public record MovementReceiptDTO(
        Long id, Long accountId, String accountNumber, String accountHolder, TransactionType type,
        TransactionCategory category, BigDecimal amount, String description, LocalDateTime date,
        BigDecimal balanceAfter, String counterparty, String counterpartyHolder) {
}
