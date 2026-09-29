package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.TransactionCategory;
import com.mindhub.homebanking.domain.TransactionType;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Query parameters for an account's movements; every field is optional.
 *
 * @param from first day included (bank's local date)
 * @param to   last day included
 * @param q    text contained in the description (case-insensitive)
 */
public record MovementFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        TransactionType type,
        TransactionCategory category,
        @Size(max = 50) String q) {
}
