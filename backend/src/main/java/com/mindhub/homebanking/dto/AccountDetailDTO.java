package com.mindhub.homebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** An account with its movements, newest first. */
public record AccountDetailDTO(
        Long id, String number, LocalDateTime creationDate, BigDecimal balance,
        List<TransactionDTO> transactions) {
}
