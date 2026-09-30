package com.mindhub.homebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** @param cbu 22 digits; {@code alias} is what the owner shares to receive money */
public record AccountDTO(Long id, String number, String cbu, String alias, LocalDateTime creationDate,
                         BigDecimal balance) {
}
