package com.mindhub.homebanking.service;

import com.mindhub.homebanking.config.BankingProperties;
import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.AuditEvent;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.ScheduledTransfer;
import com.mindhub.homebanking.dto.CreateScheduledTransferRequest;
import com.mindhub.homebanking.dto.ScheduledTransferDTO;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.ScheduledTransferRepository;
import com.mindhub.homebanking.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Transfers scheduled for later (once, weekly or monthly). Scheduling one to another client authorizes
 * all its occurrences, so the 2FA code a transfer of that amount needs is asked for then. Each
 * occurrence runs in its own transaction under a row lock: it happens once even with several
 * instances, and a failure (no funds, daily limit, closed account) is recorded, notified and skipped.
 */
@Service
@Transactional(readOnly = true)
public class ScheduledTransferService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferService.class);
    private static final String SYSTEM = "SYSTEM";
    private static final EnumSet<ScheduledTransfer.Status> OPEN =
            EnumSet.of(ScheduledTransfer.Status.ACTIVE, ScheduledTransfer.Status.PAUSED);

    private final ScheduledTransferRepository repository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final RecipientResolver recipients;
    private final TransferService transferService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;
    private final int maxOpen;
    private final TransactionTemplate newTransaction;

    public ScheduledTransferService(ScheduledTransferRepository repository, ClientRepository clientRepository,
                                    AccountRepository accountRepository, RecipientResolver recipients,
                                    TransferService transferService, NotificationService notifications,
                                    AuditService audit, Clock clock, BankingProperties properties,
                                    PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.recipients = recipients;
        this.transferService = transferService;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
        this.maxOpen = properties.scheduledTransfers().maxOpen();
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Newest first. */
    public List<ScheduledTransferDTO> findMine(String email) {
        return repository.findMine(client(email)).stream().map(ScheduledTransferService::toDto).toList();
    }

    /**
     * 404 for a source that is not the caller's or an unknown destination; 422 for the same account, a
     * start date that is not in the future, too many open ones or a bad CBU; 403 when a 2FA code is needed.
     */
    @Transactional
    public ScheduledTransferDTO create(String email, CreateScheduledTransferRequest request) {
        Long targetId = recipients.resolveId(request.targetAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
        Client client = clientRepository.findByEmailForUpdate(email) // single-use 2FA codes need the lock
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        Account source = accountRepository.findIdByNumber(request.sourceAccountNumber().trim().toUpperCase(Locale.ROOT))
                .flatMap(accountRepository::findWithClientById)
                .filter(Account::isActive)
                .filter(a -> a.getClient().getId().equals(client.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        Account target = accountRepository.findWithClientById(targetId)
                .filter(Account::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
        if (source.getId().equals(target.getId())) {
            throw new BusinessRuleException("You cannot transfer to the same account");
        }
        if (!request.startDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("The first transfer must be from tomorrow on",
                    Map.of("code", "START_DATE"));
        }
        if (repository.countByClientAndStatusIn(client, OPEN) >= maxOpen) {
            throw new BusinessRuleException("You can have up to " + maxOpen + " scheduled transfers",
                    Map.of("code", "TOO_MANY_SCHEDULED", "max", maxOpen));
        }
        if (!target.getClient().getId().equals(client.getId())) {
            transferService.authorizeInAdvance(client, target, request.amount(), request.secondFactorCode());
        }
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        ScheduledTransfer scheduled = repository.save(new ScheduledTransfer(client, source, target.getNumber(),
                ContactService.maskedName(target.getClient()), request.amount(), description, request.frequency(),
                request.startDate(), request.maxRuns(), LocalDateTime.now(clock)));
        audit.success(AuditAction.SCHEDULED_TRANSFER_CREATED, source.getNumber() + " -> " + target.getNumber(),
                "amount=" + request.amount().toPlainString() + " " + request.frequency() + " from " + request.startDate());
        return toDto(scheduled);
    }

    /** Recurring ones only (422 for a one-off); 404 if not the caller's. */
    @Transactional
    public ScheduledTransferDTO pause(String email, Long id) {
        ScheduledTransfer scheduled = own(email, id);
        if (scheduled.getFrequency() == ScheduledTransfer.Frequency.ONCE) {
            throw new BusinessRuleException("A one-off transfer cannot be paused: cancel it instead");
        }
        scheduled.pause();
        return toDto(scheduled);
    }

    /** Occurrences missed while paused are skipped. */
    @Transactional
    public ScheduledTransferDTO resume(String email, Long id) {
        ScheduledTransfer scheduled = own(email, id);
        scheduled.resume(LocalDate.now(clock));
        return toDto(scheduled);
    }

    @Transactional
    public ScheduledTransferDTO cancel(String email, Long id) {
        ScheduledTransfer scheduled = own(email, id);
        if (scheduled.isOpen()) {
            scheduled.cancel();
            audit.success(AuditAction.SCHEDULED_TRANSFER_CANCELLED, "id=" + id, null);
        }
        return toDto(scheduled);
    }

    /**
     * Runs every occurrence due by {@code today}. Called by the daily job (and at start-up).
     *
     * @return how many transfers were made
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public int runDue(LocalDate today) {
        int done = 0;
        for (Long id : repository.findDueIds(today)) {
            try {
                if (Boolean.TRUE.equals(newTransaction.execute(status -> runOne(id, today)))) {
                    done++;
                }
            } catch (BusinessRuleException | ResourceNotFoundException e) {
                // Rolled back as a whole; record why in a transaction of its own and move on.
                recordFailure(id, today, reason(e));
            } catch (RuntimeException e) {
                // Unexpected (e.g. the database): nothing was recorded, so it is retried on the next run.
                log.error("Could not run scheduled transfer {}", id, e);
            }
        }
        return done;
    }

    private boolean runOne(Long id, LocalDate today) {
        ScheduledTransfer scheduled = repository.findByIdForUpdate(id).orElse(null);
        if (scheduled == null || !scheduled.isDue(today)) {
            return false; // already run by another instance, paused or cancelled meanwhile
        }
        transferService.executeScheduled(scheduled);
        scheduled.recordRun(LocalDateTime.now(clock), ScheduledTransfer.Outcome.DONE, null);
        notifications.scheduledTransferDone(scheduled);
        return true;
    }

    private void recordFailure(Long id, LocalDate today, String reason) {
        try {
            newTransaction.executeWithoutResult(status -> {
                ScheduledTransfer scheduled = repository.findByIdForUpdate(id).orElse(null);
                if (scheduled == null || !scheduled.isDue(today)) {
                    return;
                }
                scheduled.recordRun(LocalDateTime.now(clock), ScheduledTransfer.Outcome.FAILED, reason);
                Client client = scheduled.getClient();
                audit.record(client.getEmail(), SYSTEM, AuditAction.SCHEDULED_TRANSFER_FAILED, "id=" + id,
                        AuditEvent.Outcome.FAILURE, reason);
                notifications.scheduledTransferFailed(scheduled);
            });
        } catch (RuntimeException e) {
            log.error("Could not record the failure of scheduled transfer {}", id, e);
        }
    }

    /** Shown to the client (in Spanish): why an occurrence could not be made. */
    static String reason(RuntimeException e) {
        if (e instanceof ResourceNotFoundException) {
            return "La cuenta de origen o la de destino ya no está disponible";
        }
        Object code = ((BusinessRuleException) e).getProperties().get("code");
        if ("DAILY_LIMIT_EXCEEDED".equals(code)) {
            return "Superaba tu límite diario para transferir a terceros";
        }
        if ("Insufficient funds".equals(e.getMessage())) {
            return "No había saldo suficiente en la cuenta de origen";
        }
        return "No cumplía las condiciones para transferir";
    }

    private ScheduledTransfer own(String email, Long id) {
        return repository.findByIdAndClient(id, client(email))
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled transfer not found"));
    }

    private Client client(String email) {
        return clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    private static ScheduledTransferDTO toDto(ScheduledTransfer s) {
        return new ScheduledTransferDTO(s.getId(), s.getSource().getId(), s.getSource().getNumber(),
                s.getTargetAccountNumber(), s.getTargetHolder(), s.getAmount(), s.getDescription(),
                s.getFrequency().name(), s.getStartDate(), s.getNextRun(), s.getRuns(), s.getMaxRuns(),
                s.getStatus().name(), s.getLastRunAt(), s.getLastOutcome() == null ? null : s.getLastOutcome().name(),
                s.getLastError(), s.getCreatedAt());
    }
}
