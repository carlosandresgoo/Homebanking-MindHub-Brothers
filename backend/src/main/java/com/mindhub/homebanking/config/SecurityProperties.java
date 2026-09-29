package com.mindhub.homebanking.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull RefreshToken refreshToken,
        @Valid @NotNull Cors cors,
        @Valid @NotNull LoginRateLimit loginRateLimit,
        @Valid @NotNull AccountLockout accountLockout) {

    /** Per-account brute-force protection (complements the per-IP rate limit). */
    public record AccountLockout(@Min(1) int maxAttempts, @NotNull Duration duration) {
    }

    /**
     * {@code secret} may be blank only in the dev/test profiles, where a random key is generated. In dev,
     * {@code devKeyFile} persists that random key so sessions survive restarts.
     */
    public record Jwt(String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl, String devKeyFile) {
    }

    public record RefreshToken(@NotNull Duration ttl, @NotBlank String cookieName, boolean cookieSecure) {
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public record LoginRateLimit(@Min(1) long capacity, @NotNull Duration period) {
    }
}
