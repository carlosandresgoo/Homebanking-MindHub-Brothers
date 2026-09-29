package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.CardColor;
import com.mindhub.homebanking.domain.CardType;
import jakarta.validation.constraints.NotNull;

/** Input for {@code POST /api/clients/current/cards}; unknown enum values are rejected with 400. */
public record IssueCardRequest(@NotNull CardType type, @NotNull CardColor color) {
}
