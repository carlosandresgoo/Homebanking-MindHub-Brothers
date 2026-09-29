package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 72) String password) {

    /** Keeps the password out of logs and exception messages. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
