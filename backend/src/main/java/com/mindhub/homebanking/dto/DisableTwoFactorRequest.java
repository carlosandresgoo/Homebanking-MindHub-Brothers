package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Turning 2FA off needs both factors: the password and a current code. */
public record DisableTwoFactorRequest(
        @NotBlank @Size(max = 72) String password,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code) {

    @Override
    public String toString() {
        return "DisableTwoFactorRequest[]";
    }
}
