package com.mindhub.homebanking.service;

import com.mindhub.homebanking.config.BankingProperties;
import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.AuditEvent;
import com.mindhub.homebanking.domain.Client;
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

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
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
                           BankingProperties properties, Clock clock) {
        this.accountRepository = accountRepository;
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
        String sourceNumber = normalize(request.sourceAccountNumber());
        // Account number, CBU or alias; resolved before any lock (it may reject an invalid CBU).
        Long targetId = recipients.resolveId(request.targetAccountNumber()).orElseThrow(TransferService::targetNotFound);

        Client client = clientRepository.findByEmailForUpdate(email).orElseThrow(TransferService::sourceNotFound);
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
        if (!source.hasFunds(request.amount())) {
            throw new BusinessRuleException("Insufficient funds");
        }
        if (!target.getClient().getId().equals(client.getId())) {
            checkThirdPartyRules(client, request, target);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String note = request.description() == null || request.description().isBlank()
                ? "" : " · " + request.description().trim();
        Transaction debit = source.debit(request.amount(), TransactionCategory.TRANSFER_OUT,
                "Transferencia a " + target.getNumber() + note, now).withCounterparty(target.getNumber());
        Transaction credit = target.credit(request.amount(), TransactionCategory.TRANSFER_IN,
                "Transferencia de " + source.getNumber() + note, now).withCounterparty(source.getNumber());
        transactionRepository.save(debit);
        transactionRepository.save(credit);
        audit.success(AuditAction.TRANSFER, source.getNumber() + " -> " + target.getNumber(),
                "amount=" + request.amount().toPlainString());
        if (!target.getClient().getId().equals(client.getId())) {
            notifications.transfer(client, source.getNumber(), target.getClient(), target.getNumber(),
                    request.amount(), request.description(), now);
        }

        return new TransferReceiptDTO(debit.getId(), source.getId(), source.getNumber(), target.getNumber(),
                request.amount(), debit.getDescription(), now, source.getBalance());
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

    /** Daily limit, then (with 2FA enabled) a code for large amounts. */
    private void checkThirdPartyRules(Client client, TransferRequest request, Account target) {
        BigDecimal remaining = dailyLimit(client).subtract(usedToday(client)).max(BigDecimal.ZERO);
        if (request.amount().compareTo(remaining) > 0) {
            throw new BusinessRuleException("Daily transfer limit exceeded",
                    Map.of("code", "DAILY_LIMIT_EXCEEDED", "remaining", remaining));
        }
        if (client.isTwoFactorEnabled() && request.amount().compareTo(limits.secondFactorThreshold()) >= 0) {
            try {
                twoFactor.require(client, request.secondFactorCode());
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
