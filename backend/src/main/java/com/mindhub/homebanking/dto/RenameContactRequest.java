package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RenameContactRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = ContactAlias.PATTERN, message = ContactAlias.MESSAGE) String alias) {
}
