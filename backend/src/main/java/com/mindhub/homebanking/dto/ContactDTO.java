package com.mindhub.homebanking.dto;

import java.time.LocalDateTime;

/** @param holderDisplay masked holder name, e.g. "Lucía P." */
public record ContactDTO(Long id, String alias, String accountNumber, String holderDisplay, LocalDateTime createdAt) {
}
