package com.mindhub.homebanking.support;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Role;
import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.domain.TransactionCategory;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.AuditEventRepository;
import com.mindhub.homebanking.repository.CardRepository;
import com.mindhub.homebanking.repository.ClientLoanRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.ContactRepository;
import com.mindhub.homebanking.repository.FixedTermRepository;
import com.mindhub.homebanking.repository.IdempotencyRecordRepository;
import com.mindhub.homebanking.repository.NotificationRepository;
import com.mindhub.homebanking.repository.PasswordResetTokenRepository;
import com.mindhub.homebanking.repository.RefreshTokenRepository;
import com.mindhub.homebanking.repository.ScheduledTransferRepository;
import com.mindhub.homebanking.repository.TransactionRepository;
import com.mindhub.homebanking.security.LoginRateLimiter;
import com.mindhub.homebanking.service.AccountNumberGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Resets the database to: Melba (CLIENT, account VIN001 with 5000.00), Other (CLIENT, VIN999 with 1.00)
 * and an ADMIN without accounts. Balances are created through movements, like in production.
 * Registered through {@code @Import} in {@link IntegrationTest} (not component-scanned).
 */
public class TestData {

    public static final String PASSWORD = "correct-horse-battery";
    public static final String CLIENT_EMAIL = "melba@test.com";
    public static final String OTHER_CLIENT_EMAIL = "other@test.com";
    public static final String ADMIN_EMAIL = "admin@test.com";

    private final ClientRepository clients;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CardRepository cards;
    private final ClientLoanRepository clientLoans;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final AuditEventRepository auditEvents;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final ContactRepository contacts;
    private final FixedTermRepository fixedTerms;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;
    private final Clock clock;
    private final NotificationRepository notifications;
    private final ScheduledTransferRepository scheduledTransfers;
    private final AccountNumberGenerator accountNumbers;

    public TestData(ClientRepository clients, AccountRepository accounts, TransactionRepository transactions,
                    CardRepository cards, ClientLoanRepository clientLoans, RefreshTokenRepository refreshTokens,
                    PasswordResetTokenRepository resetTokens, AuditEventRepository auditEvents,
                    IdempotencyRecordRepository idempotencyRecords, ContactRepository contacts,
                    FixedTermRepository fixedTerms,
                    PasswordEncoder passwordEncoder,
                    LoginRateLimiter loginRateLimiter, Clock clock, AccountNumberGenerator accountNumbers,
                    NotificationRepository notifications, ScheduledTransferRepository scheduledTransfers) {
        this.scheduledTransfers = scheduledTransfers;
        this.notifications = notifications;
        this.accountNumbers = accountNumbers;
        this.clock = clock;
        this.auditEvents = auditEvents;
        this.idempotencyRecords = idempotencyRecords;
        this.contacts = contacts;
        this.fixedTerms = fixedTerms;
        this.clientLoans = clientLoans;
        this.resetTokens = resetTokens;
        this.clients = clients;
        this.accounts = accounts;
        this.transactions = transactions;
        this.cards = cards;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Transactional
    public Ids reset() {
        loginRateLimiter.reset();
        refreshTokens.deleteAllInBatch();
        resetTokens.deleteAllInBatch();
        auditEvents.deleteAllInBatch();
        notifications.deleteAllInBatch();
        idempotencyRecords.deleteAllInBatch();
        scheduledTransfers.deleteAllInBatch();
        contacts.deleteAllInBatch();
        fixedTerms.deleteAllInBatch();
        cards.deleteAllInBatch();
        clientLoans.deleteAllInBatch();
        transactions.deleteAllInBatch();
        accounts.deleteAllInBatch();
        clients.deleteAllInBatch();

        String hash = passwordEncoder.encode(PASSWORD);
        Client melba = clients.save(new Client("Melba", "Morel", CLIENT_EMAIL, hash, Role.CLIENT));
        Account account = account(melba, "VIN001", new BigDecimal("5000.00"));

        Client other = clients.save(new Client("Other", "Client", OTHER_CLIENT_EMAIL, hash, Role.CLIENT));
        Account otherAccount = account(other, "VIN999", new BigDecimal("1.00"));

        clients.save(new Client("Admin", "Test", ADMIN_EMAIL, hash, Role.ADMIN));
        return new Ids(melba.getId(), other.getId(), account.getId(), otherAccount.getId());
    }

    /**
     * Opens an account for {@code owner}; a positive {@code initial} is added as a deposit. Its alias is
     * predictable: the number in lower case plus ".test" (VIN001 → vin001.test).
     */
    @Transactional
    public Account account(Client owner, String number, BigDecimal initial) {
        Account account = accountNumbers.newAccount(number, LocalDateTime.now(clock));
        account.changeAlias(number.toLowerCase(java.util.Locale.ROOT) + ".test");
        owner.addAccount(account);
        accounts.save(account);
        if (initial.signum() > 0) {
            transactions.save(account.credit(initial, TransactionCategory.DEPOSIT, "Depósito inicial", LocalDateTime.now(clock)));
        }
        return account;
    }

    /** Opens another account for the client with that e-mail. */
    @Transactional
    public Account account(String ownerEmail, String number, BigDecimal initial) {
        return account(clients.findByEmailIgnoreCase(ownerEmail).orElseThrow(), number, initial);
    }

    /** Adds a movement dated {@code date} (credit or debit) to the account with that number. */
    @Transactional
    public Transaction movement(String accountNumber, boolean credit, String amount, TransactionCategory category,
                                String description, LocalDateTime date) {
        Account account = accounts.findById(accounts.findIdByNumber(accountNumber).orElseThrow()).orElseThrow();
        BigDecimal value = new BigDecimal(amount);
        return transactions.save(credit ? account.credit(value, category, description, date)
                : account.debit(value, category, description, date));
    }

    public record Ids(Long clientId, Long otherClientId, Long accountId, Long otherAccountId) {
    }
}
