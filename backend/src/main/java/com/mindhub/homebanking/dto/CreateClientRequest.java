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
        // Names: letters of any language (José, Núñez, Zoë) with single spaces, apostrophes or hyphens between parts.
        @NotBlank @Size(max = 50) @Pattern(regexp = PERSON_NAME, message = PERSON_NAME_MESSAGE) String name,
        @NotBlank @Size(max = 50) @Pattern(regexp = PERSON_NAME, message = PERSON_NAME_MESSAGE) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 12, max = 72, message = "must be between 12 and 72 characters") String password) {

    public static final String PERSON_NAME = "^\\p{L}+(?:[ '\\-]\\p{L}+)*$";
    public static final String PERSON_NAME_MESSAGE = "must contain letters, single spaces, apostrophes or hyphens";

    /** Keeps the password out of logs and exception messages. */
    @Override
    public String toString() {
        return "CreateClientRequest[email=" + email + "]";
    }
}
