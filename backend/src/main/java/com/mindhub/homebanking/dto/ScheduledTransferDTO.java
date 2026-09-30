package com.mindhub.homebanking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @param nextRun      null once finished or cancelled
 * @param runs         executions so far (successful or not)
 * @param lastOutcome  DONE or FAILED; {@code lastError} says why it failed (in Spanish)
 */
public record ScheduledTransferDTO(Long id, Long sourceAccountId, String sourceAccountNumber,
                                   String targetAccountNumber, String targetHolder, BigDecimal amount,
                                   String description, String frequency, LocalDate startDate, LocalDate nextRun,
                                   int runs, Integer maxRuns, String status, LocalDateTime lastRunAt,
                                   String lastOutcome, String lastError, LocalDateTime createdAt) {
}
