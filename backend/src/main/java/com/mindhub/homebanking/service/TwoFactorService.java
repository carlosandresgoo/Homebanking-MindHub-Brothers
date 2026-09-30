package com.mindhub.homebanking.service;

import com.mindhub.homebanking.config.SecurityProperties;
import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.dto.TwoFactorSetupDTO;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ConflictException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.exception.SecondFactorException;
import com.mindhub.homebanking.mapper.ClientMapper;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.security.LoginRateLimiter;
import com.mindhub.homebanking.security.SecretCipher;
import com.mindhub.homebanking.security.Totp;
import com.mindhub.homebanking.service.notification.EmailTemplateService;
import com.mindhub.homebanking.service.notification.Mailer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.OptionalLong;

/**
 * Optional TOTP second factor. Enrollment is two-step (setup shows the secret, enable confirms a code
 * from the app), so a client cannot lock themselves out with a mistyped secret. Code checks are
 * rate-limited per client and each code is accepted only once.
 */
@Service
public class TwoFactorService {

    private final ClientRepository clientRepository;
    private final ClientMapper mapper;
    private final SecretCipher cipher;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;
    private final EmailTemplateService emailTemplate;
    private final Mailer mailer;
    private final AuditService audit;
    private final Clock clock;
    private final String issuer;

    public TwoFactorService(ClientRepository clientRepository, ClientMapper mapper, SecretCipher cipher,
                            PasswordEncoder passwordEncoder, LoginRateLimiter rateLimiter,
                            EmailTemplateService emailTemplate, Mailer mailer, AuditService audit,
                            Clock clock, SecurityProperties properties) {
        this.clientRepository = clientRepository;
        this.mapper = mapper;
        this.cipher = cipher;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.emailTemplate = emailTemplate;
        this.mailer = mailer;
        this.audit = audit;
        this.clock = clock;
        this.issuer = properties.twoFactor().issuer();
    }

    /** Generates a new secret (replacing any unconfirmed one). 409 when 2FA is already enabled. */
    @Transactional
    public TwoFactorSetupDTO setup(String email) {
        Client client = lockClient(email);
        if (client.isTwoFactorEnabled()) {
            throw new ConflictException("Two-factor authentication is already enabled");
        }
        byte[] secret = Totp.newSecret();
        client.startTwoFactorEnrollment(cipher.encrypt(secret));
        return new TwoFactorSetupDTO(Totp.base32(secret), Totp.otpauthUri(issuer, client.getEmail(), secret));
    }

    /** Confirms the enrollment with a first code from the app. */
    @Transactional
    public ClientDTO enable(String email, String code) {
        Client client = lockClient(email);
        if (client.isTwoFactorEnabled()) {
            throw new ConflictException("Two-factor authentication is already enabled");
        }
        if (client.getTotpSecret() == null) {
            throw new BusinessRuleException("Start the two-factor setup first");
        }
        client.enableTwoFactor(check(client, code));
        audit.success(AuditAction.TWO_FACTOR_ENABLED, client.getEmail(), null);

        // Notificar por email
        String body = emailTemplate.twoFactorEnabled(client.getName());
        mailer.send(client.getEmail(), "Autenticación de 2 factores habilitada", body);

        return mapper.toDto(client);
    }

    /** Needs the password and a current code, so a hijacked session alone cannot turn it off. */
    @Transactional
    public ClientDTO disable(String email, String password, String code) {
        Client client = lockClient(email);
        if (!client.isTwoFactorEnabled()) {
            throw new ConflictException("Two-factor authentication is not enabled");
        }
        rateLimiter.consume(rateLimitKey(client));
        if (!passwordEncoder.matches(password, client.getPassword())) {
            throw new BusinessRuleException("The current password is incorrect");
        }
        check(client, code);
        client.disableTwoFactor();
        audit.success(AuditAction.TWO_FACTOR_DISABLED, client.getEmail(), null);

        // Notificar por email
        String body = emailTemplate.twoFactorDisabled(client.getName());
        mailer.send(client.getEmail(), "Autenticación de 2 factores deshabilitada", body);

        return mapper.toDto(client);
    }

    /** Admin recovery for a client who lost their phone. */
    @Transactional
    public ClientDTO reset(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        client.disableTwoFactor();
        audit.success(AuditAction.TWO_FACTOR_RESET, client.getEmail(), null);
        return mapper.toDto(client);
    }

    /** Sign-in check after the password was verified: a no-op for clients without 2FA. */
    @Transactional
    public void verifyLogin(String email, String code) {
        Client client = lockClient(email);
        if (client.isTwoFactorEnabled()) {
            require(client, code);
        }
    }

    /**
     * Requires a valid code from a client with 2FA enabled and marks it as used. The caller must hold
     * the client's row lock (see {@link ClientRepository#findByEmailForUpdate}) in its transaction.
     *
     * @throws SecondFactorException REQUIRED without a code, INVALID for a wrong or reused one
     */
    public void require(Client client, String code) {
        if (code == null || code.isBlank()) {
            throw new SecondFactorException(SecondFactorException.Reason.REQUIRED);
        }
        client.registerSecondFactorStep(check(client, code));
    }

    private long check(Client client, String code) {
        rateLimiter.consume(rateLimitKey(client));
        OptionalLong step = Totp.verify(cipher.decrypt(client.getTotpSecret()), code, clock.instant(),
                client.getTotpLastStep());
        if (step.isEmpty()) {
            throw new SecondFactorException(SecondFactorException.Reason.INVALID);
        }
        return step.getAsLong();
    }

    private Client lockClient(String email) {
        return clientRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    private static String rateLimitKey(Client client) {
        return "2fa:" + client.getId();
    }
}
