package com.mindhub.homebanking.security;

import com.mindhub.homebanking.config.SecurityProperties;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.RefreshToken;
import com.mindhub.homebanking.exception.InvalidRefreshTokenException;
import com.mindhub.homebanking.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque, rotating refresh tokens. Each use revokes the presented token and issues a new one;
 * presenting an already revoked token is treated as theft and revokes every token of that client.
 */
@Service
public class RefreshTokenService {

    /** Result of a successful rotation: the owner and the new raw token to send back in the cookie. */
    public record Rotation(Client client, String rawToken) {
    }

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String issue(Client client) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(properties.refreshToken().ttl());
        repository.save(new RefreshToken(hash(raw), client, expiresAt));
        return raw;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String raw) {
        RefreshToken token = find(raw);
        Client client = token.getClient();
        if (token.isRevoked()) {
            // Reuse of a rotated token: someone else may hold the chain. Kill all sessions of the client.
            repository.revokeAllByClient(client);
            log.warn("Refresh token reuse detected for client id={}; all sessions revoked", client.getId());
            throw new InvalidRefreshTokenException("Refresh token reused");
        }
        if (token.isExpired(clock.instant())) {
            token.revoke();
            throw new InvalidRefreshTokenException("Refresh token expired");
        }
        token.revoke();
        return new Rotation(client, issue(client));
    }

    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(hash(raw)).ifPresent(RefreshToken::revoke);
    }

    @Transactional
    public int purgeExpired() {
        return repository.deleteExpired(clock.instant());
    }

    private RefreshToken find(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRefreshTokenException("Missing refresh token");
        }
        return repository.findByTokenHash(hash(raw))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
