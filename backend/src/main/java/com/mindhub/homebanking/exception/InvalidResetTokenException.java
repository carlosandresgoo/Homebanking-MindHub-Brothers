package com.mindhub.homebanking.exception;

/** Unknown, expired or already used password reset link. Mapped to a generic 400. */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("The link is invalid or has expired");
    }
}
