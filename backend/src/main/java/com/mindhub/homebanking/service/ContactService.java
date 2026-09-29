package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Contact;
import com.mindhub.homebanking.dto.ContactDTO;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ConflictException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.ContactRepository;
import com.mindhub.homebanking.security.LoginRateLimiter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Saved recipients. Adding one confirms the account exists and shows a masked holder name, so it is
 * rate-limited per client (no bulk discovery of account numbers). Another client's contact answers 404.
 */
@Service
@Transactional(readOnly = true)
public class ContactService {

    static final int MAX_CONTACTS = 50;

    private final ContactRepository contactRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;
    private final Clock clock;

    public ContactService(ContactRepository contactRepository, ClientRepository clientRepository,
                          AccountRepository accountRepository, LoginRateLimiter rateLimiter, AuditService audit,
                          Clock clock) {
        this.contactRepository = contactRepository;
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
    }

    public List<ContactDTO> findMine(String email) {
        return contactRepository.findByClientOrderByAliasAsc(client(email)).stream()
                .map(ContactService::toDto)
                .toList();
    }

    /**
     * 404 for an unknown or closed account; 422 for one's own account or when the agenda is full;
     * 409 when the account or the alias is already saved.
     */
    @Transactional
    public ContactDTO add(String email, String accountNumber, String alias) {
        Client client = client(email);
        rateLimiter.consume("contact:" + client.getId());
        String number = accountNumber.trim().toUpperCase(Locale.ROOT);
        String cleanAlias = normalizeAlias(alias);

        Account account = accountRepository.findIdByNumber(number)
                .flatMap(accountRepository::findWithClientById)
                .filter(Account::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (account.getClient().getId().equals(client.getId())) {
            throw new BusinessRuleException("Your own accounts are always available: no need to save them");
        }
        if (contactRepository.countByClient(client) >= MAX_CONTACTS) {
            throw new BusinessRuleException("You can save up to " + MAX_CONTACTS + " recipients");
        }
        if (contactRepository.existsByClientAndAccountNumber(client, account.getNumber())) {
            throw new ConflictException("This account is already in your recipients");
        }
        if (contactRepository.existsByClientAndAliasIgnoreCase(client, cleanAlias)) {
            throw new ConflictException("You already have a recipient with that alias");
        }
        Contact contact = contactRepository.save(new Contact(client, cleanAlias, account.getNumber(),
                maskedName(account.getClient()), LocalDateTime.now(clock)));
        audit.success(AuditAction.CONTACT_ADDED, account.getNumber(), null);
        return toDto(contact);
    }

    @Transactional
    public ContactDTO rename(String email, Long id, String alias) {
        Client client = client(email);
        Contact contact = own(client, id);
        String cleanAlias = normalizeAlias(alias);
        if (contactRepository.existsByClientAndAliasIgnoreCaseAndIdNot(client, cleanAlias, id)) {
            throw new ConflictException("You already have a recipient with that alias");
        }
        contact.rename(cleanAlias);
        return toDto(contact);
    }

    @Transactional
    public void delete(String email, Long id) {
        Contact contact = own(client(email), id);
        contactRepository.delete(contact);
        audit.success(AuditAction.CONTACT_REMOVED, contact.getAccountNumber(), null);
    }

    private Contact own(Client client, Long id) {
        return contactRepository.findByIdAndClient(id, client)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));
    }

    private Client client(String email) {
        return clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    /** "Lucía Pérez" → "Lucía P.": recognisable, without exposing the full name. */
    static String maskedName(Client holder) {
        String lastName = holder.getLastName().strip();
        return holder.getName().strip() + (lastName.isEmpty() ? "" : " " + lastName.substring(0, 1) + ".");
    }

    private static String normalizeAlias(String alias) {
        return alias.strip().replaceAll("\\s+", " ");
    }

    private static ContactDTO toDto(Contact contact) {
        return new ContactDTO(contact.getId(), contact.getAlias(), contact.getAccountNumber(),
                contact.getHolderDisplay(), contact.getCreatedAt());
    }
}
