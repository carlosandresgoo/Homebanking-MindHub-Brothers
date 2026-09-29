package com.mindhub.homebanking.domain;

/** Security- and money-relevant events kept in the audit trail. */
public enum AuditAction {
    LOGIN,
    LOGIN_LOCKED,
    LOGOUT,
    REGISTER,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET,
    CLIENT_CREATED,
    CLIENT_BLOCKED,
    CLIENT_UNBLOCKED,
    ACCOUNT_OPENED,
    ACCOUNT_CLOSED,
    TRANSFER,
    CARD_ISSUED,
    CARD_DEACTIVATED,
    LOAN_APPROVED,
    LOAN_INSTALLMENT_PAID
}
