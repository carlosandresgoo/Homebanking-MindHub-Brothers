package com.mindhub.homebanking.domain;

/** What a movement was for; used to filter movements and to apply limits. */
public enum TransactionCategory {
    DEPOSIT,
    TRANSFER_OUT,
    TRANSFER_IN,
    LOAN_DISBURSEMENT,
    LOAN_PAYMENT,
    /** Money moved from an account into a fixed term (not an expense). */
    FIXED_TERM_DEPOSIT,
    /** The principal coming back at maturity (not income). */
    FIXED_TERM_PAYOUT,
    /** The interest earned at maturity (income). */
    FIXED_TERM_INTEREST,
    OTHER
}
