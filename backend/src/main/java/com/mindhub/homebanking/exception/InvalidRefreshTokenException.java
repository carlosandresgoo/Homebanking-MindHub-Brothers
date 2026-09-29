package com.mindhub.homebanking.exception;

/** Missing, unknown, expired, revoked or cross-origin refresh attempt. Always mapped to a generic 401. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
