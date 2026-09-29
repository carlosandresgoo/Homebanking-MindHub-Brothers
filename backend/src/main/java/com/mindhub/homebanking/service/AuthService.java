package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.AuditEvent;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.exception.AccountLockedException;
import com.mindhub.homebanking.exception.SecondFactorException;
import org.springframework.security.core.AuthenticationException;
import com.mindhub.homebanking.dto.CreateClientRequest;
import com.mindhub.homebanking.dto.TokenResponse;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.security.AccessTokenService;
import com.mindhub.homebanking.security.RefreshTokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /** Access token for the response body plus the raw refresh token for the HttpOnly cookie. */
    public record Session(TokenResponse token, String refreshToken) {
    }

    private final AuthenticationManager authenticationManager;
    private final ClientRepository clientRepository;
    private final ClientService clientService;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttempts;
    private final AuditService audit;
    private final TwoFactorService twoFactor;

    public AuthService(AuthenticationManager authenticationManager, ClientRepository clientRepository,
                       ClientService clientService, AccessTokenService accessTokenService,
                       RefreshTokenService refreshTokenService, LoginAttemptService loginAttempts,
                       AuditService audit, TwoFactorService twoFactor) {
        this.twoFactor = twoFactor;
        this.loginAttempts = loginAttempts;
        this.audit = audit;
        this.authenticationManager = authenticationManager;
        this.clientRepository = clientRepository;
        this.clientService = clientService;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * @throws org.springframework.security.core.AuthenticationException on bad credentials
     * @throws com.mindhub.homebanking.exception.AccountLockedException when blocked or temporarily locked
     */
    public Session login(String email, String password) {
        return login(email, password, null);
    }

    /**
     * @param secondFactorCode required when the client has 2FA enabled
     * @throws SecondFactorException after a correct password when the code is missing or wrong (a wrong
     *                               code counts as a failed login for the lockout)
     */
    public Session login(String email, String password, String secondFactorCode) {
        try {
            loginAttempts.checkAllowed(email);
        } catch (AccountLockedException e) {
            audit.record(email, null, AuditAction.LOGIN_LOCKED, null, AuditEvent.Outcome.FAILURE, e.getReason().name());
            throw e;
        }
        try {
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (AuthenticationException e) {
            loginAttempts.recordFailure(email);
            audit.record(email, null, AuditAction.LOGIN, null, AuditEvent.Outcome.FAILURE, "bad credentials");
            throw e;
        }
        try {
            twoFactor.verifyLogin(email, secondFactorCode);
        } catch (SecondFactorException e) {
            if (e.getReason() == SecondFactorException.Reason.INVALID) {
                loginAttempts.recordFailure(email);
                audit.record(email, null, AuditAction.LOGIN, null, AuditEvent.Outcome.FAILURE, "bad second factor");
            }
            throw e;
        }
        loginAttempts.recordSuccess(email);
        Client client = clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        audit.record(client.getEmail(), client.getRole().name(), AuditAction.LOGIN, null,
                AuditEvent.Outcome.SUCCESS, null);
        return new Session(accessToken(client), refreshTokenService.issue(client));
    }

    /** Public sign-up: creates a CLIENT with an initial account and starts a session right away. */
    public Session register(CreateClientRequest request) {
        Client client = clientService.register(request);
        audit.record(client.getEmail(), client.getRole().name(), AuditAction.REGISTER, client.getEmail(),
                AuditEvent.Outcome.SUCCESS, null);
        return startSession(client);
    }

    /** New access + refresh tokens for an already authenticated client (e.g. after a password change). */
    public Session startSession(Client client) {
        return new Session(accessToken(client), refreshTokenService.issue(client));
    }

    public Session refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        return new Session(accessToken(rotation.client()), rotation.rawToken());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken).ifPresent(client -> audit.record(client.getEmail(),
                    client.getRole().name(), AuditAction.LOGOUT, null, AuditEvent.Outcome.SUCCESS, null));
        }
    }

    private TokenResponse accessToken(Client client) {
        return TokenResponse.bearer(accessTokenService.issue(client), accessTokenService.ttlSeconds(), client.getRole());
    }
}
