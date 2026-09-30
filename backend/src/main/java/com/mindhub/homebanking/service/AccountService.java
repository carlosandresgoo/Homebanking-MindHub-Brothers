package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.AccountAlias;
import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.FixedTerm;
import com.mindhub.homebanking.repository.FixedTermRepository;
import com.mindhub.homebanking.dto.AccountDTO;
import com.mindhub.homebanking.dto.AccountDetailDTO;
import com.mindhub.homebanking.dto.RecipientDTO;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ConflictException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.mapper.ClientMapper;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.security.LoginRateLimiter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Account use cases. Every lookup by id checks ownership and answers 404 for accounts the caller may
 * not see, so ids of other clients' accounts cannot be probed. Recipient lookups and alias changes are
 * rate-limited per client, so they cannot be used to harvest holders or aliases in bulk.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    public static final int MAX_ACTIVE_ACCOUNTS = 3;
    static final String BANK_NAME = "MindHub Brothers";

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final AccountNumberGenerator accountNumbers;
    private final ClientMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    private final FixedTermRepository fixedTermRepository;
    private final RecipientResolver recipients;
    private final LoginRateLimiter rateLimiter;

    public AccountService(AccountRepository accountRepository, ClientRepository clientRepository,
                          AccountNumberGenerator accountNumbers, ClientMapper mapper, AuditService audit,
                          Clock clock, FixedTermRepository fixedTermRepository, RecipientResolver recipients,
                          LoginRateLimiter rateLimiter) {
        this.fixedTermRepository = fixedTermRepository;
        this.recipients = recipients;
        this.rateLimiter = rateLimiter;
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
        Account account = accountNumbers.newAccount(LocalDateTime.now(clock));
        client.addAccount(account);
        Account saved = accountRepository.save(account);
        audit.success(AuditAction.ACCOUNT_OPENED, saved.getNumber(), null);
        return mapper.toDto(saved);
    }

    /**
     * Who would receive a transfer to {@code key} (account number, CBU or alias), with a masked name.
     * 404 for unknown or closed accounts; 422 for an invalid CBU or one of another bank.
     */
    public RecipientDTO lookup(String email, String key) {
        Client client = clientRepository.findByEmailIgnoreCase(email).orElseThrow(AccountService::notFound);
        rateLimiter.consume("lookup:" + client.getId());
        Account account = recipients.resolveId(key)
                .flatMap(accountRepository::findWithClientById)
                .filter(Account::isActive)
                .orElseThrow(AccountService::notFound);
        Client holder = account.getClient();
        return new RecipientDTO(account.getNumber(), account.getCbu(), account.getAlias(),
                ContactService.maskedName(holder), BANK_NAME, holder.getId().equals(client.getId()));
    }

    /** Owner only: 404 otherwise; 409 when another account uses it; 422 for a reserved one. */
    @Transactional
    public AccountDTO changeAlias(Long id, String email, String alias) {
        Account account = accountRepository.findWithClientById(id)
                .filter(Account::isActive)
                .filter(a -> isOwner(a, email))
                .orElseThrow(AccountService::notFound);
        String normalized = AccountAlias.normalize(alias);
        if (normalized.equals(account.getAlias())) {
            return mapper.toDto(account);
        }
        rateLimiter.consume("alias:" + account.getClient().getId());
        if (AccountAlias.isReserved(normalized)) {
            throw new BusinessRuleException("That alias is reserved", Map.of("code", "ALIAS_RESERVED"));
        }
        if (accountRepository.existsByAliasIgnoreCaseAndIdNot(normalized, id)) {
            throw new ConflictException("That alias is already taken");
        }
        String previous = account.getAlias();
        account.changeAlias(normalized);
        audit.success(AuditAction.ACCOUNT_ALIAS_CHANGED, account.getNumber(), previous + " -> " + normalized);
        return mapper.toDto(account);
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
        if (fixedTermRepository.existsByAccountAndStatus(account, FixedTerm.Status.ACTIVE)) {
            throw new ConflictException("The account has an active fixed term that will be paid into it");
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
