package com.mindhub.homebanking.service;

import com.mindhub.homebanking.config.BankingProperties;
import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.AuditEvent;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.FixedTerm;
import com.mindhub.homebanking.domain.FixedTermPlan;
import com.mindhub.homebanking.domain.TransactionCategory;
import com.mindhub.homebanking.dto.CreateFixedTermRequest;
import com.mindhub.homebanking.dto.FixedTermDTO;
import com.mindhub.homebanking.dto.FixedTermPlanDTO;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.FixedTermPlanRepository;
import com.mindhub.homebanking.repository.FixedTermRepository;
import com.mindhub.homebanking.repository.TransactionRepository;
import com.mindhub.homebanking.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fixed-term deposits: constitution (debits the account), automatic renewal flag and the payout at
 * maturity (credits principal and interest, then reinvests when auto-renew is on). Each payout runs in
 * its own transaction under a row lock, so a failure or a second instance never pays twice.
 */
@Service
@Transactional(readOnly = true)
public class FixedTermService {

    private static final Logger log = LoggerFactory.getLogger(FixedTermService.class);
    private static final String SYSTEM = "SYSTEM";

    private final FixedTermRepository fixedTermRepository;
    private final FixedTermPlanRepository planRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;
    private final BigDecimal minAmount;
    private final TransactionTemplate newTransaction;

    public FixedTermService(FixedTermRepository fixedTermRepository, FixedTermPlanRepository planRepository,
                            ClientRepository clientRepository, AccountRepository accountRepository,
                            TransactionRepository transactionRepository, AuditService audit,
                            NotificationService notifications, Clock clock,
                            BankingProperties properties, PlatformTransactionManager transactionManager) {
        this.fixedTermRepository = fixedTermRepository;
        this.planRepository = planRepository;
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.minAmount = properties.fixedTerms().minAmount();
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<FixedTermPlanDTO> plans() {
        return planRepository.findAllByOrderByTermDaysAsc().stream()
                .map(p -> new FixedTermPlanDTO(p.getTermDays(), p.getAnnualRate()))
                .toList();
    }

    /** Active first (soonest maturity first), then the paid ones. */
    public List<FixedTermDTO> findMine(String email) {
        return fixedTermRepository.findByClientOrderByStatusAscMaturityDateAscIdAsc(client(email)).stream()
                .map(FixedTermService::toDto)
                .toList();
    }

    /** 404 account not mine; 422 below the minimum, unknown term or insufficient funds. */
    @Transactional
    public FixedTermDTO create(String email, CreateFixedTermRequest request) {
        Client client = client(email);
        if (request.amount().compareTo(minAmount) < 0) {
            throw new BusinessRuleException("The minimum amount is " + minAmount.toPlainString(),
                    Map.of("code", "BELOW_MINIMUM", "minAmount", minAmount));
        }
        FixedTermPlan plan = planRepository.findByTermDays(request.termDays())
                .orElseThrow(() -> new BusinessRuleException("There is no fixed term of " + request.termDays() + " days"));
        Account account = accountRepository.findIdByNumber(request.accountNumber().trim().toUpperCase(Locale.ROOT))
                .flatMap(accountRepository::findByIdForUpdate)
                .filter(Account::isActive)
                .filter(a -> a.getClient().getId().equals(client.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.hasFunds(request.amount())) {
            throw new BusinessRuleException("Insufficient funds");
        }
        FixedTerm fixedTerm = constitute(client, account, request.amount(), plan, request.autoRenew(), null);
        audit.success(AuditAction.FIXED_TERM_CREATED, account.getNumber(),
                "amount=" + request.amount().toPlainString() + " days=" + plan.getTermDays());
        notifications.fixedTermCreated(fixedTerm);
        return toDto(fixedTerm);
    }

    /** Only while it is active; 404 if it is not the caller's. */
    @Transactional
    public FixedTermDTO setAutoRenew(String email, Long id, boolean autoRenew) {
        FixedTerm fixedTerm = fixedTermRepository.findByIdAndClient(id, client(email))
                .orElseThrow(() -> new ResourceNotFoundException("Fixed term not found"));
        if (fixedTerm.getStatus() != FixedTerm.Status.ACTIVE) {
            throw new BusinessRuleException("This fixed term was already paid");
        }
        fixedTerm.setAutoRenew(autoRenew);
        return toDto(fixedTerm);
    }

    /**
     * Pays every fixed term due by {@code today}. Called by the daily job (and at start-up).
     *
     * @return how many were paid
     */
    public int payDue(LocalDate today) {
        int paid = 0;
        for (Long id : fixedTermRepository.findDueIds(today)) {
            try {
                Boolean done = newTransaction.execute(status -> payOne(id, today));
                if (Boolean.TRUE.equals(done)) {
                    paid++;
                }
            } catch (RuntimeException e) {
                // One broken fixed term must not stop the others; it is retried on the next run.
                log.error("Could not pay fixed term {}", id, e);
            }
        }
        return paid;
    }

    private boolean payOne(Long id, LocalDate today) {
        FixedTerm fixedTerm = fixedTermRepository.findByIdForUpdate(id).orElse(null);
        if (fixedTerm == null || !fixedTerm.isDue(today)) {
            return false; // already paid by another run
        }
        Account account = accountRepository.findByIdForUpdate(fixedTerm.getAccount().getId()).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
        String label = "Plazo fijo N.º " + fixedTerm.getId();
        transactionRepository.save(account.credit(fixedTerm.getPrincipal(), TransactionCategory.FIXED_TERM_PAYOUT,
                label + ": capital", now));
        if (fixedTerm.getInterest().signum() > 0) {
            transactionRepository.save(account.credit(fixedTerm.getInterest(), TransactionCategory.FIXED_TERM_INTEREST,
                    label + ": intereses", now));
        }
        fixedTerm.markPaid(now);
        Client client = fixedTerm.getClient();
        audit.record(client.getEmail(), SYSTEM, AuditAction.FIXED_TERM_PAID, account.getNumber(),
                AuditEvent.Outcome.SUCCESS, "id=" + fixedTerm.getId());

        FixedTerm renewed = null;
        if (fixedTerm.isAutoRenew() && account.isActive()) {
            renewed = planRepository.findByTermDays(fixedTerm.getTermDays()).map(plan -> {
                FixedTerm next = constitute(client, account, fixedTerm.getTotal(), plan, true, fixedTerm.getId());
                audit.record(client.getEmail(), SYSTEM, AuditAction.FIXED_TERM_RENEWED, account.getNumber(),
                        AuditEvent.Outcome.SUCCESS, "id=" + fixedTerm.getId() + " -> " + next.getId());
                return next;
            }).orElse(null);
        }
        notifications.fixedTermPaid(fixedTerm, renewed);
        return true;
    }

    /** Debits the account and creates the fixed term at the plan's current rate, starting today. */
    private FixedTerm constitute(Client client, Account account, BigDecimal amount, FixedTermPlan plan,
                                 boolean autoRenew, Long renewedFrom) {
        LocalDate today = LocalDate.now(clock);
        FixedTerm fixedTerm = fixedTermRepository.save(
                new FixedTerm(client, account, amount, plan, today, autoRenew, renewedFrom));
        transactionRepository.save(account.debit(amount, TransactionCategory.FIXED_TERM_DEPOSIT,
                "Plazo fijo N.º " + fixedTerm.getId() + " a " + plan.getTermDays() + " días",
                LocalDateTime.now(clock)));
        return fixedTerm;
    }

    private Client client(String email) {
        return clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    private static FixedTermDTO toDto(FixedTerm f) {
        return new FixedTermDTO(f.getId(), f.getAccount().getId(), f.getAccount().getNumber(), f.getPrincipal(),
                f.getAnnualRate(), f.getTermDays(), f.getInterest(), f.getTotal(), f.getStartDate(),
                f.getMaturityDate(), f.isAutoRenew(), f.getStatus().name(), f.getPaidAt());
    }
}
