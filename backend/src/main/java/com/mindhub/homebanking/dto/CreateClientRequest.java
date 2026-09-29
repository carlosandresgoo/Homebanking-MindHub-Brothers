package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Input for {@code POST /api/clients}. Only these fields can be set by the caller: the role is always
 * {@code CLIENT} and ids/accounts are server-controlled (no mass assignment).
 */
public record CreateClientRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[a-zA-Z]+$", message = "must contain letters only") String name,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[a-zA-Z]+$", message = "must contain letters only") String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 12, max = 72, message = "must be between 12 and 72 characters") String password) {

    /** Keeps the password out of logs and exception messages. */
    @Override
    public String toString() {
        return "CreateClientRequest[email=" + email + "]";
    }
}
