package com.mindhub.homebanking.security;

import com.mindhub.homebanking.config.SecurityProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for secrets that must be read back (TOTP seeds): a database dump alone does
 * not reveal them. The key comes from {@code TOTP_ENCRYPTION_KEY} (required in production).
 * Stored format: Base64(12-byte IV || ciphertext+tag).
 */
@Component
public class SecretCipher {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(SecurityProperties properties, Environment environment) {
        SecurityProperties.TwoFactor config = properties.twoFactor();
        byte[] bytes = KeyMaterial.resolve("TOTP_ENCRYPTION_KEY", config.encryptionKey(), KEY_BYTES,
                config.devKeyFile(), environment);
        // Exactly 256 bits: a longer configured value is truncated deterministically.
        this.key = new SecretKeySpec(bytes, 0, KEY_BYTES, "AES");
    }

    public String encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot encrypt secret", e);
        }
    }

    public byte[] decrypt(String stored) {
        try {
            byte[] data = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot decrypt secret (wrong TOTP_ENCRYPTION_KEY?)", e);
        }
    }
}
