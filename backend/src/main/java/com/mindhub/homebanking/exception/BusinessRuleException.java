package com.mindhub.homebanking.exception;

/** A well-formed request that breaks a business rule (e.g. insufficient funds). Mapped to 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
