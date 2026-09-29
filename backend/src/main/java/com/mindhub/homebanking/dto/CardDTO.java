package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.CardColor;
import com.mindhub.homebanking.domain.CardType;

import java.time.LocalDate;

/** A card as listed: masked (last 4 digits only), never with CVV. */
public record CardDTO(
        Long id, String cardholder, CardType type, CardColor color, String last4,
        LocalDate fromDate, LocalDate thruDate, boolean expired) {
}
