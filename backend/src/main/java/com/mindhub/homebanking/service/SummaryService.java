package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.domain.TransactionCategory;
import com.mindhub.homebanking.domain.TransactionType;
import com.mindhub.homebanking.dto.FinancialSummaryDTO;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Dashboard figures, computed from the client's movements of the last months. */
@Service
@Transactional(readOnly = true)
public class SummaryService {

    public static final int MAX_MONTHS = 12;

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public SummaryService(ClientRepository clientRepository, AccountRepository accountRepository,
                          TransactionRepository transactionRepository, Clock clock) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /** @param months how many calendar months, including the current one (1..{@value #MAX_MONTHS}) */
    public FinancialSummaryDTO summary(String email, int months) {
        Client client = clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        int span = Math.clamp(months, 1, MAX_MONTHS);
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        LocalDate start = current.minusMonths(span - 1L).atDay(1);

        List<Account> accounts = accountRepository.findByClient(client);
        Set<String> ownNumbers = accounts.stream().map(Account::getNumber).collect(Collectors.toSet());
        BigDecimal total = accounts.stream().filter(Account::isActive).map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<YearMonth, BigDecimal[]> byMonth = new LinkedHashMap<>();
        for (int i = span - 1; i >= 0; i--) {
            byMonth.put(current.minusMonths(i), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        Map<TransactionCategory, BigDecimal> expenses = new EnumMap<>(TransactionCategory.class);
        Map<LocalDate, BigDecimal> netByDay = new HashMap<>();

        for (Transaction t : transactionRepository.findByClientSince(client, start.atStartOfDay())) {
            boolean credit = t.getType() == TransactionType.CREDIT;
            LocalDate day = t.getDate().toLocalDate();
            netByDay.merge(day, credit ? t.getAmount() : t.getAmount().negate(), BigDecimal::add);
            if (isInternal(t, ownNumbers)) {
                continue; // the client's money changing place, not coming in or going out
            }
            BigDecimal[] month = byMonth.get(YearMonth.from(day));
            if (month != null) {
                month[credit ? 0 : 1] = month[credit ? 0 : 1].add(t.getAmount());
            }
            if (!credit) {
                expenses.merge(t.getCategory(), t.getAmount(), BigDecimal::add);
            }
        }

        List<FinancialSummaryDTO.Month> monthList = byMonth.entrySet().stream()
                .map(e -> new FinancialSummaryDTO.Month(e.getKey().atDay(1), e.getValue()[0], e.getValue()[1]))
                .toList();
        List<FinancialSummaryDTO.CategoryTotal> categoryList = expenses.entrySet().stream()
                .sorted(Map.Entry.<TransactionCategory, BigDecimal>comparingByValue().reversed())
                .map(e -> new FinancialSummaryDTO.CategoryTotal(e.getKey(), e.getValue()))
                .toList();
        return new FinancialSummaryDTO(total, monthList, categoryList, history(total, netByDay, start, today));
    }

    /** Walks back from today's total, undoing each day's net movements. */
    private static List<FinancialSummaryDTO.DailyBalance> history(BigDecimal total, Map<LocalDate, BigDecimal> netByDay,
                                                                  LocalDate start, LocalDate today) {
        List<FinancialSummaryDTO.DailyBalance> points = new ArrayList<>();
        BigDecimal balance = total;
        for (LocalDate day = today; !day.isBefore(start); day = day.minusDays(1)) {
            points.add(new FinancialSummaryDTO.DailyBalance(day, balance));
            balance = balance.subtract(netByDay.getOrDefault(day, BigDecimal.ZERO));
        }
        Collections.reverse(points);
        return points;
    }

    /**
     * Transfers between the client's own accounts, and money going into or back from a fixed term
     * (its interest, though, is income).
     */
    private static boolean isInternal(Transaction t, Set<String> ownNumbers) {
        TransactionCategory category = t.getCategory();
        if (category == TransactionCategory.FIXED_TERM_DEPOSIT || category == TransactionCategory.FIXED_TERM_PAYOUT) {
            return true;
        }
        return (category == TransactionCategory.TRANSFER_OUT || category == TransactionCategory.TRANSFER_IN)
                && t.getCounterparty() != null && ownNumbers.contains(t.getCounterparty());
    }
}
