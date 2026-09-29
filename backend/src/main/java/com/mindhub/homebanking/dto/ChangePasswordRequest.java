package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 12, max = 72, message = "must be between 12 and 72 characters") String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
