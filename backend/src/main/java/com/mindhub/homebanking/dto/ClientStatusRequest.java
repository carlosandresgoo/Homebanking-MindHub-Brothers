package com.mindhub.homebanking.dto;

import jakarta.validation.constraints.NotNull;

/** Input for {@code PATCH /api/clients/{id}/status}: {@code false} blocks the client, {@code true} unblocks it. */
public record ClientStatusRequest(@NotNull Boolean enabled) {
}
