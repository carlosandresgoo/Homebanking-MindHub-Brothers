package com.mindhub.homebanking.service;

import com.mindhub.homebanking.config.SecurityProperties;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.exception.AccountLockedException;
import com.mindhub.homebanking.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Per-account lockout. The status check runs before the password is verified, so while an account is
 * locked a guesser learns nothing about the password; each counter update is its own transaction so it
 * is kept even though the login itself fails.
 */
@Service
public class LoginAttemptService {

    private final ClientRepository clientRepository;
    private final SecurityProperties.AccountLockout policy;
    private final Clock clock;

    public LoginAttemptService(ClientRepository clientRepository, SecurityProperties properties, Clock clock) {
        this.clientRepository = clientRepository;
        this.policy = properties.accountLockout();
        this.clock = clock;
    }

    /** @throws AccountLockedException when the account exists and is blocked or temporarily locked */
    @Transactional(readOnly = true)
    public void checkAllowed(String email) {
        clientRepository.findByEmailIgnoreCase(email).ifPresent(client -> {
            if (!client.isEnabled()) {
                throw new AccountLockedException(AccountLockedException.Reason.BLOCKED, null);
            }
            Instant now = clock.instant();
            if (client.isLocked(now)) {
                throw new AccountLockedException(AccountLockedException.Reason.LOCKED, client.getLockedUntil());
            }
        });
    }

    @Transactional
    public void recordFailure(String email) {
        clientRepository.findByEmailIgnoreCase(email).ifPresent(client ->
                client.registerFailedLogin(clock.instant(), policy.maxAttempts(), policy.duration()));
    }

    @Transactional
    public void recordSuccess(String email) {
        clientRepository.findByEmailIgnoreCase(email).ifPresent(Client::registerSuccessfulLogin);
    }
}
