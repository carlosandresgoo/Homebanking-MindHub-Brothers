package com.mindhub.homebanking.domain;

/** What a movement was for; used to filter movements and to apply limits. */
public enum TransactionCategory {
    DEPOSIT,
    TRANSFER_OUT,
    TRANSFER_IN,
    LOAN_DISBURSEMENT,
    LOAN_PAYMENT,
    OTHER
}
