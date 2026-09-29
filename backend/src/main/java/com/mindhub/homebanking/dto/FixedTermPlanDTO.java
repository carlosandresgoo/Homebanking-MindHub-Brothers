package com.mindhub.homebanking.dto;

import java.math.BigDecimal;

/** @param annualRate TNA as a fraction (0.35 = 35 %) */
public record FixedTermPlanDTO(int termDays, BigDecimal annualRate) {
}
