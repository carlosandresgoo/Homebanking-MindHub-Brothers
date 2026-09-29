package com.mindhub.homebanking.exception;

/**
 * The operation needs a valid authenticator code. Mapped to 403 with a {@code secondFactor} property
 * ({@code REQUIRED}: send one; {@code INVALID}: wrong, expired or already used) so the client can ask
 * for the code and retry.
 */
public class SecondFactorException extends RuntimeException {

    public enum Reason { REQUIRED, INVALID }

    private final Reason reason;

    public SecondFactorException(Reason reason) {
        super(reason == Reason.REQUIRED ? "Second factor required" : "Invalid second factor code");
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
