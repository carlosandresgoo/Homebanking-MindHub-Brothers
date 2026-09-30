package com.mindhub.homebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An account's header; its movements are paginated at {@code /api/accounts/{id}/transactions}. */
public record AccountDetailDTO(Long id, String number, String cbu, String alias, LocalDateTime creationDate,
                               BigDecimal balance) {
}
