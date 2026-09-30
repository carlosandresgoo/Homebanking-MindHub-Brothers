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
import com.mindhub.homebanking.service.export.MovementsWorkbook;
import com.mindhub.homebanking.service.export.StatementDocument;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An account's movements: filtered pages, CSV and Excel export, PDF statements and single-movement
 * receipts. Visible to the account's owner and to admins; anyone else gets 404.
 */
@Service
@Transactional(readOnly = true)
public class MovementService {

    public static final int MAX_PAGE_SIZE = 100;
    static final int MAX_EXPORT_ROWS = 10_000;

    /** A statement covers at most this many days. */
    static final int MAX_STATEMENT_DAYS = 366;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "date", "id");
    private static final Sort OLDEST_FIRST = Sort.by(Sort.Direction.ASC, "date", "id");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<TransactionCategory, String> CATEGORY_LABEL = new EnumMap<>(Map.of(
            TransactionCategory.DEPOSIT, "Depósito",
            TransactionCategory.TRANSFER_OUT, "Transferencia enviada",
            TransactionCategory.TRANSFER_IN, "Transferencia recibida",
            TransactionCategory.LOAN_DISBURSEMENT, "Préstamo acreditado",
            TransactionCategory.LOAN_PAYMENT, "Cuota de préstamo",
            TransactionCategory.FIXED_TERM_DEPOSIT, "Plazo fijo constituido",
            TransactionCategory.FIXED_TERM_PAYOUT, "Plazo fijo: capital",
            TransactionCategory.FIXED_TERM_INTEREST, "Plazo fijo: intereses",
            TransactionCategory.OTHER, "Otro"));

    /** A file to download: its name and bytes. */
    public record FileExport(String filename, byte[] content) {
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

    /**
     * Same filters as {@link #page}, newest first, up to {@value #MAX_EXPORT_ROWS} rows, as UTF-8 CSV
     * (with BOM, so Excel shows accents correctly).
     */
    public FileExport exportCsv(Long accountId, String email, boolean admin, MovementFilter filter) {
        Account account = accountService.findVisible(accountId, email, admin);
        List<Transaction> rows = exportRows(account, filter);

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
        return new FileExport(exportName(account, "csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Same rows as {@link #exportCsv}, as an Excel workbook with real dates and numbers. */
    public FileExport exportXlsx(Long accountId, String email, boolean admin, MovementFilter filter) {
        Account account = accountService.findVisible(accountId, email, admin);
        byte[] content = MovementsWorkbook.write(account.getNumber(), exportRows(account, filter),
                t -> CATEGORY_LABEL.getOrDefault(t.getCategory(), "Otro"));
        return new FileExport(exportName(account, "xlsx"), content);
    }

    /**
     * The PDF statement of {@code from}..{@code to} (both inclusive; defaults: this month so far). 422 for
     * a reversed or too long range ({@value #MAX_STATEMENT_DAYS} days) or too many movements.
     */
    public FileExport statement(Long accountId, String email, boolean admin, LocalDate from, LocalDate to) {
        Account account = accountService.findVisible(accountId, email, admin);
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to == null || to.isAfter(today) ? today : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_STATEMENT_DAYS) {
            throw new BusinessRuleException("A statement covers up to " + MAX_STATEMENT_DAYS + " days",
                    Map.of("code", "RANGE_TOO_LONG"));
        }
        Specification<Transaction> period = spec(account, new MovementFilter(start, end, null, null, null));
        if (transactionRepository.count(period) > MAX_EXPORT_ROWS) {
            throw new BusinessRuleException("Too many movements: choose a shorter period",
                    Map.of("code", "TOO_MANY_MOVEMENTS"));
        }
        List<Transaction> movements = transactionRepository
                .findAll(period, PageRequest.of(0, MAX_EXPORT_ROWS, OLDEST_FIRST)).getContent();
        BigDecimal opening = transactionRepository
                .findFirstByAccountAndDateBeforeOrderByDateDescIdDesc(account, start.atStartOfDay())
                .map(Transaction::getBalanceAfter)
                .orElse(BigDecimal.ZERO);
        var client = account.getClient();
        byte[] pdf = StatementDocument.write(new StatementDocument.Statement(
                client.getName() + " " + client.getLastName(), account.getNumber(), account.getCbu(),
                account.getAlias(), start, end, opening, movements, LocalDateTime.now(clock)));
        String filename = "resumen-" + account.getNumber() + "-" + start.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + end.format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf";
        return new FileExport(filename, pdf);
    }

    private List<Transaction> exportRows(Account account, MovementFilter filter) {
        return transactionRepository
                .findAll(spec(account, filter), PageRequest.of(0, MAX_EXPORT_ROWS, NEWEST_FIRST))
                .getContent();
    }

    private String exportName(Account account, String extension) {
        return "movimientos-" + account.getNumber() + "-"
                + LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE) + "." + extension;
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
