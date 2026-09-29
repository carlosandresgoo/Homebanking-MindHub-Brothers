package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Input for {@code POST /api/clients/current/contacts}. The account number is case-insensitive. */
public record CreateContactRequest(
        @NotBlank @Size(max = 20) String accountNumber,
        @NotBlank @Size(max = 40) @Pattern(regexp = ContactAlias.PATTERN, message = ContactAlias.MESSAGE) String alias) {
}
