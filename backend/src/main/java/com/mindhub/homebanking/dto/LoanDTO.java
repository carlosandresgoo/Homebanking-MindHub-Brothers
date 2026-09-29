package com.mindhub.homebanking.dto;

import java.math.BigDecimal;
import java.util.List;

/** A loan product of the catalog. */
public record LoanDTO(Long id, String code, String name, BigDecimal maxAmount, BigDecimal interestRate,
                      List<Integer> payments) {
}
