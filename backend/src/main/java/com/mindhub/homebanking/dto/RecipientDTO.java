package com.mindhub.homebanking.dto;

/**
 * Who receives a transfer, shown before confirming it ({@code GET /api/accounts/lookup}). The client then
 * transfers to {@code accountNumber}, so a later alias change cannot redirect the money.
 *
 * @param holderDisplay masked holder name, e.g. "Lucía P."
 * @param own           the account is one of the caller's
 */
public record RecipientDTO(String accountNumber, String cbu, String alias, String holderDisplay, String bank,
                           boolean own) {
}
