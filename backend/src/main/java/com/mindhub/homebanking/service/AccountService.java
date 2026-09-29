package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.dto.AccountDTO;
import com.mindhub.homebanking.dto.AccountDetailDTO;
import com.mindhub.homebanking.exception.ConflictException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.mapper.ClientMapper;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Account use cases. Every lookup by id checks ownership and answers 404 for accounts the caller may
 * not see, so ids of other clients' accounts cannot be probed.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    public static final int MAX_ACTIVE_ACCOUNTS = 3;

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final AccountNumberGenerator accountNumbers;
    private final ClientMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, ClientRepository clientRepository,
                          AccountNumberGenerator accountNumbers, ClientMapper mapper, AuditService audit,
                          Clock clock) {
        this.audit = audit;
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.accountNumbers = accountNumbers;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<AccountDTO> findMine(String email) {
        return accountRepository.findByClientEmailIgnoreCaseAndActiveTrueOrderByIdAsc(email).stream()
                .map(mapper::toDto)
                .toList();
    }

    /** Owner or ADMIN only; closed accounts are not visible. */
    public AccountDetailDTO findDetail(Long id, String email, boolean admin) {
        return mapper.toDetailDto(findVisible(id, email, admin));
    }

    /** The account if the caller may see it (owner or ADMIN, and still open); 404 otherwise. */
    public Account findVisible(Long id, String email, boolean admin) {
        return accountRepository.findWithClientById(id)
                .filter(Account::isActive)
                .filter(a -> admin || isOwner(a, email))
                .orElseThrow(AccountService::notFound);
    }

    @Transactional
    public AccountDTO open(String email) {
        Client client = clientRepository.findByEmailIgnoreCase(email).orElseThrow(AccountService::notFound);
        if (accountRepository.countByClientAndActiveTrue(client) >= MAX_ACTIVE_ACCOUNTS) {
            throw new ConflictException("You already have the maximum of " + MAX_ACTIVE_ACCOUNTS + " active accounts");
        }
        Account account = new Account(accountNumbers.next(), LocalDateTime.now(clock), BigDecimal.ZERO);
        client.addAccount(account);
        Account saved = accountRepository.save(account);
        audit.success(AuditAction.ACCOUNT_OPENED, saved.getNumber(), null);
        return mapper.toDto(saved);
    }

    /** Soft close: only the owner, and only with a zero balance (money is never lost). */
    @Transactional
    public void close(Long id, String email) {
        Account account = accountRepository.findWithClientById(id)
                .filter(Account::isActive)
                .filter(a -> isOwner(a, email))
                .orElseThrow(AccountService::notFound);
        if (!account.hasZeroBalance()) {
            throw new ConflictException("The account must have a zero balance to be closed");
        }
        account.close();
        audit.success(AuditAction.ACCOUNT_CLOSED, account.getNumber(), null);
    }

    private static boolean isOwner(Account account, String email) {
        return account.getClient().getEmail().equalsIgnoreCase(email);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Account not found");
    }
}
