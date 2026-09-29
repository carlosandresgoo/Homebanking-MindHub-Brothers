package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A 6-digit code from the authenticator app. */
public record TwoFactorCodeRequest(@NotBlank @Pattern(regexp = "\\d{6}", message = "must be 6 digits") String code) {
}
