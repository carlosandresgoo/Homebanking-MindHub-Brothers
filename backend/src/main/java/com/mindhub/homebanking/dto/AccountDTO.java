package com.mindhub.homebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountDTO(Long id, String number, LocalDateTime creationDate, BigDecimal balance) {
}
