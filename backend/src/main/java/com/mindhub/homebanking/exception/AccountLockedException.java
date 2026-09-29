package com.mindhub.homebanking.exception;

import java.time.Instant;

/** Sign-in refused because the account is blocked by an admin or temporarily locked. Mapped to 423. */
public class AccountLockedException extends RuntimeException {

    public enum Reason { BLOCKED, LOCKED }

    private final Reason reason;
    private final Instant lockedUntil;

    public AccountLockedException(Reason reason, Instant lockedUntil) {
        super(reason == Reason.BLOCKED ? "Account blocked" : "Account temporarily locked");
        this.reason = reason;
        this.lockedUntil = lockedUntil;
    }

    public Reason getReason() {
        return reason;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
