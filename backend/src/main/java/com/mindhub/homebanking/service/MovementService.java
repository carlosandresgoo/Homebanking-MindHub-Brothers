package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.domain.TransactionCategory;
import com.mindhub.homebanking.domain.TransactionType;
import com.mindhub.homebanking.dto.MovementFilter;
import com.mindhub.homebanking.dto.MovementReceiptDTO;
import com.mindhub.homebanking.dto.PageDTO;
import com.mindhub.homebanking.dto.TransactionDTO;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.mapper.ClientMapper;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An account's movements: filtered pages, CSV export and single-movement receipts. Visible to the
 * account's owner and to admins; anyone else gets 404.
 */
@Service
@Transactional(readOnly = true)
public class MovementService {

    public static final int MAX_PAGE_SIZE = 100;
    static final int MAX_EXPORT_ROWS = 10_000;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "date", "id");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<TransactionCategory, String> CATEGORY_LABEL = new EnumMap<>(Map.of(
            TransactionCategory.DEPOSIT, "Depósito",
            TransactionCategory.TRANSFER_OUT, "Transferencia enviada",
            TransactionCategory.TRANSFER_IN, "Transferencia recibida",
            TransactionCategory.LOAN_DISBURSEMENT, "Préstamo acreditado",
            TransactionCategory.LOAN_PAYMENT, "Cuota de préstamo",
            TransactionCategory.OTHER, "Otro"));

    /** The CSV file: name and UTF-8 bytes (with BOM, so Excel shows accents correctly). */
    public record CsvExport(String filename, byte[] content) {
    }

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ClientMapper mapper;
    private final Clock clock;

    public MovementService(AccountService accountService, AccountRepository accountRepository,
                           TransactionRepository transactionRepository, ClientMapper mapper, Clock clock) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public PageDTO<TransactionDTO> page(Long accountId, String email, boolean admin, MovementFilter filter,
                                        int page, int size) {
        Account account = accountService.findVisible(accountId, email, admin);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), NEWEST_FIRST);
        return PageDTO.of(transactionRepository.findAll(spec(account, filter), pageable).map(mapper::toDto));
    }

    /** Same filters as {@link #page}, newest first, up to {@value #MAX_EXPORT_ROWS} rows. */
    public CsvExport exportCsv(Long accountId, String email, boolean admin, MovementFilter filter) {
        Account account = accountService.findVisible(accountId, email, admin);
        List<Transaction> rows = transactionRepository
                .findAll(spec(account, filter), PageRequest.of(0, MAX_EXPORT_ROWS, NEWEST_FIRST))
                .getContent();

        StringBuilder csv = new StringBuilder("﻿");
        // Semicolons and decimal commas: what a Spanish-locale spreadsheet expects.
        csv.append("Fecha;Descripción;Categoría;Tipo;Importe;Saldo\r\n");
        for (Transaction t : rows) {
            boolean credit = t.getType() == TransactionType.CREDIT;
            csv.append(t.getDate().format(CSV_DATE)).append(';')
                    .append(textCell(t.getDescription())).append(';')
                    .append(CATEGORY_LABEL.getOrDefault(t.getCategory(), "Otro")).append(';')
                    .append(credit ? "Ingreso" : "Egreso").append(';')
                    .append(credit ? "" : "-").append(decimal(t.getAmount().toPlainString())).append(';')
                    .append(decimal(t.getBalanceAfter().toPlainString())).append("\r\n");
        }
        String filename = "movimientos-" + account.getNumber() + "-" + LocalDate.now(clock).format(
                DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return new CsvExport(filename, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    public MovementReceiptDTO receipt(Long transactionId, String email, boolean admin) {
        Transaction t = transactionRepository.findWithAccountById(transactionId)
                .filter(tx -> admin || tx.getAccount().getClient().getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new ResourceNotFoundException("Movement not found"));
        Account account = t.getAccount();
        String counterpartyHolder = t.getCounterparty() == null ? null
                : accountRepository.findIdByNumber(t.getCounterparty())
                        .flatMap(accountRepository::findWithClientById)
                        .map(other -> ContactService.maskedName(other.getClient()))
                        .orElse(null);
        return new MovementReceiptDTO(t.getId(), account.getId(), account.getNumber(),
                account.getClient().getName() + " " + account.getClient().getLastName(), t.getType(),
                t.getCategory(), t.getAmount(), t.getDescription(), t.getDate(), t.getBalanceAfter(),
                t.getCounterparty(), counterpartyHolder);
    }

    private static Specification<Transaction> spec(Account account, MovementFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BusinessRuleException("'from' must not be after 'to'");
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("account"), account));
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("date"), filter.to().plusDays(1).atStartOfDay()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("description")),
                        "%" + escapeLike(filter.q().strip().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** User text is matched literally: % and _ are not wildcards. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * A free-text CSV cell. Values that a spreadsheet would run as a formula (=, +, -, @, tab, CR) are
     * prefixed with an apostrophe (CSV injection); separators, quotes and line breaks are quoted.
     */
    static String textCell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String decimal(String plain) {
        return plain.replace('.', ',');
    }
}
