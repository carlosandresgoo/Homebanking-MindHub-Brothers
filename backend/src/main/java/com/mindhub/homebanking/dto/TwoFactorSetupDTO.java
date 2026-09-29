package com.mindhub.homebanking.dto;

/**
 * Shown once while enrolling: the Base32 secret (for manual entry) and the otpauth URI (for the QR).
 */
public record TwoFactorSetupDTO(String secret, String otpauthUri) {

    /** Keeps the secret out of logs. */
    @Override
    public String toString() {
        return "TwoFactorSetupDTO[]";
    }
}
