package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param secondFactorCode authenticator code, only for clients with 2FA enabled */
public record LoginRequest(@NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 72) String password,
                           @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String secondFactorCode) {

    /** Keeps the password and the code out of logs and exception messages. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
