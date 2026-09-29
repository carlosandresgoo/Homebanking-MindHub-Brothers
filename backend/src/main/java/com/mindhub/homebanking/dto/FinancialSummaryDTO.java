package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.TransactionCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The client's finances for the dashboard. Transfers between the client's own accounts are left out
 * of income, expenses and categories (they are not money coming in or going out).
 *
 * @param months         oldest first, one entry per month (months without movements included)
 * @param expenses       spending by category over the whole period, largest first
 * @param balanceHistory total balance of all accounts at the end of each day, oldest first
 */
public record FinancialSummaryDTO(
        BigDecimal totalBalance,
        List<Month> months,
        List<CategoryTotal> expenses,
        List<DailyBalance> balanceHistory) {

    /** @param month first day of the month */
    public record Month(LocalDate month, BigDecimal income, BigDecimal expense) {
    }

    public record CategoryTotal(TransactionCategory category, BigDecimal amount) {
    }

    public record DailyBalance(LocalDate date, BigDecimal balance) {
    }
}
