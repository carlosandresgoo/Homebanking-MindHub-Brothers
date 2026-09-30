package com.mindhub.homebanking.service;

import com.mindhub.homebanking.config.BankingProperties;
import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.AuditEvent;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.ScheduledTransfer;
import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.domain.TransactionCategory;
import com.mindhub.homebanking.dto.TransferLimitsDTO;
import com.mindhub.homebanking.dto.TransferReceiptDTO;
import com.mindhub.homebanking.dto.TransferRequest;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.exception.SecondFactorException;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.ContactRepository;
import com.mindhub.homebanking.repository.TransactionRepository;
import com.mindhub.homebanking.service.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Money transfers between accounts. Both movements are written in one transaction. The client row is
 * locked first (daily limit, single-use 2FA codes), then both accounts in ascending id order (so two
 * opposite transfers cannot deadlock) before the balance check, so concurrent transfers can neither
 * spend the same money twice nor exceed the daily limit together.
 */
@Service
public class TransferService {

    private static final String SYSTEM = "SYSTEM";

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final ContactRepository contactRepository;
    private final TransactionRepository transactionRepository;
    private final TwoFactorService twoFactor;
    private final AuditService audit;
    private final NotificationService notifications;
    private final RecipientResolver recipients;
    private final BankingProperties.Transfers limits;
    private final Clock clock;

    public TransferService(AccountRepository accountRepository, ClientRepository clientRepository,
                           TransactionRepository transactionRepository, TwoFactorService twoFactor,
                           AuditService audit, NotificationService notifications, RecipientResolver recipients,
                           BankingProperties properties, Clock clock, ContactRepository contactRepository) {
        this.accountRepository = accountRepository;
        this.contactRepository = contactRepository;
        this.clientRepository = clientRepository;
        this.transactionRepository = transactionRepository;
        this.twoFactor = twoFactor;
        this.audit = audit;
        this.notifications = notifications;
        this.recipients = recipients;
        this.limits = properties.transfers();
        this.clock = clock;
    }

    @Transactional
    public TransferReceiptDTO transfer(String email, TransferRequest request) {
        // Account number, CBU or alias; resolved before any lock (it may reject an invalid CBU).
        Long targetId = recipients.resolveId(request.targetAccountNumber()).orElseThrow(TransferService::targetNotFound);
        Client client = clientRepository.findByEmailForUpdate(email).orElseThrow(TransferService::sourceNotFound);
        return move(client, request.sourceAccountNumber(), targetId, request.amount(), request.description(),
                request.secondFactorCode(), null);
    }

    /**
     * One occurrence of a scheduled transfer: the same rules as {@link #transfer}, except the second
     * factor, which the client gave when scheduling it (see {@link #authorizeInAdvance}).
     */
    @Transactional
    public TransferReceiptDTO executeScheduled(ScheduledTransfer scheduled) {
        Client client = clientRepository.findByEmailForUpdate(scheduled.getClient().getEmail())
                .orElseThrow(TransferService::sourceNotFound);
        Long targetId = accountRepository.findIdByNumber(scheduled.getTargetAccountNumber())
                .orElseThrow(TransferService::targetNotFound);
        return move(client, scheduled.getSource().getNumber(), targetId, scheduled.getAmount(),
                scheduled.getDescription(), null, scheduled);
    }

    /**
     * Scheduling a transfer to someone else authorizes every future occurrence, so it asks now for the
     * code a transfer of that amount would need. The caller holds the client's row lock.
     */
    public void authorizeInAdvance(Client client, Account target, BigDecimal amount, String secondFactorCode) {
        requireSecondFactor(client, target, amount, secondFactorCode);
    }

    /** @param scheduled the scheduled transfer being run, or null for one the client is making now */
    private TransferReceiptDTO move(Client client, String sourceNumberInput, Long targetId, BigDecimal amount,
                                    String description, String secondFactorCode, ScheduledTransfer scheduled) {
        String sourceNumber = normalize(sourceNumberInput);
        Long sourceId = accountRepository.findIdByNumber(sourceNumber).orElseThrow(TransferService::sourceNotFound);
        if (sourceId.equals(targetId)) {
            throw new BusinessRuleException("You cannot transfer to the same account");
        }

        // Lock both rows in a global order, then read their current state.
        Account first = lock(Math.min(sourceId, targetId));
        Account second = lock(Math.max(sourceId, targetId));
        Account source = first.getId().equals(sourceId) ? first : second;
        Account target = source == first ? second : first;

        if (!source.isActive() || !source.getClient().getId().equals(client.getId())) {
            throw sourceNotFound(); // not yours: indistinguishable from "does not exist"
        }
        if (!target.isActive()) {
            throw targetNotFound();
        }
        if (!source.hasFunds(amount)) {
            throw new BusinessRuleException("Insufficient funds");
        }
        boolean toOthers = !target.getClient().getId().equals(client.getId());
        if (toOthers) {
            checkDailyLimit(client, amount);
            if (scheduled == null) {
                requireSecondFactor(client, target, amount, secondFactorCode);
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String note = description == null || description.isBlank() ? "" : " · " + description.trim();
        String kind = scheduled == null ? "Transferencia" : "Transferencia programada";
        Transaction debit = source.debit(amount, TransactionCategory.TRANSFER_OUT,
                kind + " a " + target.getNumber() + note, now).withCounterparty(target.getNumber());
        Transaction credit = target.credit(amount, TransactionCategory.TRANSFER_IN,
                "Transferencia de " + source.getNumber() + note, now).withCounterparty(source.getNumber());
        transactionRepository.save(debit);
        transactionRepository.save(credit);
        String route = source.getNumber() + " -> " + target.getNumber();
        if (scheduled == null) {
            audit.success(AuditAction.TRANSFER, route, "amount=" + amount.toPlainString());
        } else { // run by the job: no signed-in user, the client is the actor
            audit.record(client.getEmail(), SYSTEM, AuditAction.TRANSFER, route, AuditEvent.Outcome.SUCCESS,
                    "amount=" + amount.toPlainString() + " scheduled=" + scheduled.getId());
        }
        if (toOthers) {
            notifications.transfer(client, source.getNumber(), target.getClient(), target, amount, description, now);
        }

        return new TransferReceiptDTO(debit.getId(), source.getId(), source.getNumber(), target.getNumber(),
                amount, debit.getDescription(), now, source.getBalance());
    }

    @Transactional(readOnly = true)
    public TransferLimitsDTO limits(String email) {
        Client client = clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        BigDecimal limit = dailyLimit(client);
        BigDecimal used = usedToday(client);
        return new TransferLimitsDTO(limit, used, limit.subtract(used).max(BigDecimal.ZERO),
                client.isTwoFactorEnabled(), limits.secondFactorThreshold(), limits.dailyLimitWithSecondFactor());
    }

    private void checkDailyLimit(Client client, BigDecimal amount) {
        BigDecimal remaining = dailyLimit(client).subtract(usedToday(client)).max(BigDecimal.ZERO);
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessRuleException("Daily transfer limit exceeded",
                    Map.of("code", "DAILY_LIMIT_EXCEEDED", "remaining", remaining));
        }
    }

    /** With 2FA enabled, a code for large amounts to others, unless the recipient is trusted. */
    private void requireSecondFactor(Client client, Account target, BigDecimal amount, String code) {
        if (client.isTwoFactorEnabled() && amount.compareTo(limits.secondFactorThreshold()) >= 0
                && !contactRepository.existsByClientAndAccountNumberAndTrustedAtIsNotNull(client, target.getNumber())) {
            try {
                twoFactor.require(client, code);
            } catch (SecondFactorException e) {
                if (e.getReason() == SecondFactorException.Reason.INVALID) {
                    audit.record(client.getEmail(), client.getRole().name(), AuditAction.TRANSFER,
                            "-> " + target.getNumber(), AuditEvent.Outcome.FAILURE, "invalid second factor");
                }
                throw e;
            }
        }
    }

    private BigDecimal dailyLimit(Client client) {
        return client.isTwoFactorEnabled() ? limits.dailyLimitWithSecondFactor() : limits.dailyLimit();
    }

    private BigDecimal usedToday(Client client) {
        return transactionRepository.sumSentToOthersSince(client, LocalDate.now(clock).atStartOfDay());
    }

    private Account lock(Long id) {
        return accountRepository.findByIdForUpdate(id).orElseThrow(TransferService::sourceNotFound);
    }

    private static String normalize(String accountNumber) {
        return accountNumber.trim().toUpperCase(Locale.ROOT);
    }

    private static ResourceNotFoundException sourceNotFound() {
        return new ResourceNotFoundException("Source account not found");
    }

    private static ResourceNotFoundException targetNotFound() {
        return new ResourceNotFoundException("Destination account not found");
    }
}
