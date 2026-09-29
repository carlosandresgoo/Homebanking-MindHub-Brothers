package com.mindhub.homebanking.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Resolves a symmetric key: from its Base64 environment value when present (required in production),
 * otherwise, only in dev/test, a random key - persisted to {@code devKeyFile} when given so it survives
 * restarts of the dev server.
 */
final class KeyMaterial {

    private static final Logger log = LoggerFactory.getLogger(KeyMaterial.class);

    private KeyMaterial() {
    }

    static byte[] resolve(String name, String base64Value, int minBytes, String devKeyFile, Environment environment) {
        if (base64Value != null && !base64Value.isBlank()) {
            byte[] key = Base64.getDecoder().decode(base64Value.trim());
            if (key.length < minBytes) {
                throw new IllegalStateException(name + " must decode to at least " + (minBytes * 8) + " bits");
            }
            return key;
        }
        if (!environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException(name + " must be set (Base64, at least " + (minBytes * 8) + " bits)");
        }
        if (devKeyFile == null || devKeyFile.isBlank()) {
            log.warn("{} not set: using a random key (invalidated on restart)", name);
            return random(minBytes);
        }
        return loadOrCreate(Path.of(devKeyFile), minBytes, name);
    }

    private static byte[] loadOrCreate(Path file, int minBytes, String name) {
        try {
            if (Files.exists(file)) {
                byte[] key = Base64.getDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).trim());
                if (key.length >= minBytes) {
                    return key;
                }
            }
            byte[] key = random(minBytes);
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, Base64.getEncoder().encodeToString(key), StandardCharsets.US_ASCII);
            log.warn("{} not set: generated a dev key in {} (git-ignored, dev only)", name, file.toAbsolutePath());
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read or write dev key file " + file, e);
        }
    }

    private static byte[] random(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
