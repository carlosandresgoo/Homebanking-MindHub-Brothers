package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.PasswordResetToken;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.InvalidResetTokenException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.PasswordResetTokenRepository;
import com.mindhub.homebanking.repository.RefreshTokenRepository;
import com.mindhub.homebanking.service.notification.Mailer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Password change (logged in) and recovery (by e-mail link). Both revoke every refresh token of the
 * client, so a leaked password or stolen session stops working everywhere.
 */
@Service
public class PasswordService {

    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final ClientRepository clientRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Mailer mailer;
    private final Clock clock;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordService(ClientRepository clientRepository, PasswordResetTokenRepository resetTokenRepository,
                           RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                           Mailer mailer, Clock clock, @Value("${app.frontend-url}") String frontendUrl) {
        this.clientRepository = clientRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    /** @return the client, so the caller can start a fresh session */
    @Transactional
    public Client change(String email, String currentPassword, String newPassword) {
        Client client = clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        if (!passwordEncoder.matches(currentPassword, client.getPassword())) {
            throw new BusinessRuleException("The current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, client.getPassword())) {
            throw new BusinessRuleException("The new password must be different from the current one");
        }
        client.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllByClient(client);
        return client;
    }

    /**
     * Sends a reset link if the e-mail belongs to a client. The caller always gets the same response,
     * so the endpoint cannot be used to find out who is a client.
     */
    @Transactional
    public void requestReset(String email) {
        clientRepository.findByEmailIgnoreCase(email).ifPresent(client -> {
            Instant now = clock.instant();
            resetTokenRepository.invalidateAll(client, now);
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            resetTokenRepository.save(new PasswordResetToken(hash(token), client, now.plus(RESET_TOKEN_TTL)));
            mailer.send(client.getEmail(), "Restablecé tu contraseña de MindHub Brothers", """
                    Hola %s,

                    Recibimos un pedido para restablecer tu contraseña. Usá este enlace (vence en %d minutos):

                    %s/reset-password?token=%s

                    Si no fuiste vos, ignorá este mensaje: tu contraseña no cambia.
                    """.formatted(client.getName(), RESET_TOKEN_TTL.toMinutes(), frontendUrl, token));
        });
    }

    @Transactional
    public void reset(String token, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(hash(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(InvalidResetTokenException::new);
        Client client = resetToken.getClient();
        client.changePassword(passwordEncoder.encode(newPassword));
        resetToken.markUsed(now);
        refreshTokenRepository.revokeAllByClient(client);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
