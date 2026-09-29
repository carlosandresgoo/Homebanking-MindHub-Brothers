package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.NotNull;

/** Only the automatic renewal of an active fixed term can change. */
public record UpdateFixedTermRequest(@NotNull Boolean autoRenew) {
}
