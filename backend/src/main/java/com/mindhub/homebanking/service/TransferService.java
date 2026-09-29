package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.dto.TransferReceiptDTO;
import com.mindhub.homebanking.dto.TransferRequest;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Money transfers between accounts. Both movements are written in one transaction, and both accounts
 * are row-locked (in ascending id order, so two opposite transfers cannot deadlock) before the
 * balance check, so concurrent transfers cannot spend the same money twice.
 */
@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public TransferService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                           Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Transactional
    public TransferReceiptDTO transfer(String email, TransferRequest request) {
        String sourceNumber = normalize(request.sourceAccountNumber());
        String targetNumber = normalize(request.targetAccountNumber());
        if (sourceNumber.equals(targetNumber)) {
            throw new BusinessRuleException("You cannot transfer to the same account");
        }

        Long sourceId = accountRepository.findIdByNumber(sourceNumber).orElseThrow(TransferService::sourceNotFound);
        Long targetId = accountRepository.findIdByNumber(targetNumber).orElseThrow(TransferService::targetNotFound);

        // Lock both rows in a global order, then read their current state.
        Account first = lock(Math.min(sourceId, targetId));
        Account second = lock(Math.max(sourceId, targetId));
        Account source = first.getId().equals(sourceId) ? first : second;
        Account target = source == first ? second : first;

        if (!source.isActive() || !source.getClient().getEmail().equalsIgnoreCase(email)) {
            throw sourceNotFound(); // not yours: indistinguishable from "does not exist"
        }
        if (!target.isActive()) {
            throw targetNotFound();
        }
        if (!source.hasFunds(request.amount())) {
            throw new BusinessRuleException("Insufficient funds");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String note = request.description() == null || request.description().isBlank()
                ? "" : " · " + request.description().trim();
        Transaction debit = source.debit(request.amount(), "Transferencia a " + target.getNumber() + note, now);
        Transaction credit = target.credit(request.amount(), "Transferencia de " + source.getNumber() + note, now);
        transactionRepository.save(debit);
        transactionRepository.save(credit);

        return new TransferReceiptDTO(debit.getId(), source.getId(), source.getNumber(), target.getNumber(),
                request.amount(), debit.getDescription(), now, source.getBalance());
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
