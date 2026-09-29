package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionDTO(
        Long id, TransactionType type, BigDecimal amount, String description, LocalDateTime date,
        BigDecimal balanceAfter) {
}
