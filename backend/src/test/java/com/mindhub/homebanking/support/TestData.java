package com.mindhub.homebanking.support;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Role;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.RefreshTokenRepository;
import com.mindhub.homebanking.security.LoginRateLimiter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resets the database to two clients (CLIENT + ADMIN) and an extra CLIENT with its own account.
 * Registered through {@code @Import} in {@link IntegrationTest} (not component-scanned).
 */
public class TestData {

    public static final String PASSWORD = "correct-horse-battery";
    public static final String CLIENT_EMAIL = "melba@test.com";
    public static final String OTHER_CLIENT_EMAIL = "other@test.com";
    public static final String ADMIN_EMAIL = "admin@test.com";

    private final ClientRepository clients;
    private final AccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;

    public TestData(ClientRepository clients, AccountRepository accounts, RefreshTokenRepository refreshTokens,
                    PasswordEncoder passwordEncoder, LoginRateLimiter loginRateLimiter) {
        this.clients = clients;
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Transactional
    public Ids reset() {
        loginRateLimiter.reset();
        refreshTokens.deleteAllInBatch();
        accounts.deleteAllInBatch();
        clients.deleteAllInBatch();

        String hash = passwordEncoder.encode(PASSWORD);
        Client melba = clients.save(new Client("Melba", "Morel", CLIENT_EMAIL, hash, Role.CLIENT));
        Account account = new Account("vin001", LocalDateTime.now(), new BigDecimal("5000.00"));
        melba.addAccount(account);
        accounts.save(account);

        Client other = clients.save(new Client("Other", "Client", OTHER_CLIENT_EMAIL, hash, Role.CLIENT));
        Account otherAccount = new Account("vin999", LocalDateTime.now(), new BigDecimal("1.00"));
        other.addAccount(otherAccount);
        accounts.save(otherAccount);

        clients.save(new Client("Admin", "Test", ADMIN_EMAIL, hash, Role.ADMIN));
        return new Ids(melba.getId(), other.getId());
    }

    public record Ids(Long clientId, Long otherClientId) {
    }
}
