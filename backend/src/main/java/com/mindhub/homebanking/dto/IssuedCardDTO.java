package com.mindhub.homebanking.dto;

/**
 * Response of card issuance: the only time the full number and CVV leave the server. They are not
 * stored, so they cannot be retrieved again.
 */
public record IssuedCardDTO(CardDTO card, String number, String cvv) {

    /** Keeps the PAN and CVV out of logs. */
    @Override
    public String toString() {
        return "IssuedCardDTO[card=" + card.id() + "]";
    }
}
