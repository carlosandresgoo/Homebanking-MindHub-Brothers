package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.AccountAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Input for {@code PATCH /api/accounts/{id}/alias}; compared and stored lower-case. */
public record UpdateAliasRequest(
        @NotBlank @Pattern(regexp = AccountAlias.PATTERN, message = AccountAlias.MESSAGE) String alias) {
}
