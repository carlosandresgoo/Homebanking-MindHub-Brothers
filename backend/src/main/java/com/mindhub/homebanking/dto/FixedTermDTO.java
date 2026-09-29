package com.mindhub.homebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @param annualRate TNA as a fraction (0.35 = 35 %)
 * @param total      principal + interest, paid on {@code maturityDate}
 * @param status     ACTIVE or PAID
 */
public record FixedTermDTO(Long id, Long accountId, String accountNumber, BigDecimal principal,
                           BigDecimal annualRate, int termDays, BigDecimal interest, BigDecimal total,
                           LocalDate startDate, LocalDate maturityDate, boolean autoRenew, String status,
                           LocalDateTime paidAt) {
}
