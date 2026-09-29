package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.service.IdempotencyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Shared bits for endpoints that accept an {@code Idempotency-Key} header. */
final class Idempotency {

    static final String HEADER = "Idempotency-Key";
    /** 8-100 URL-safe characters (a UUID from the client fits). */
    static final String KEY_PATTERN = "^[A-Za-z0-9_-]{8,100}$";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private Idempotency() {
    }

    static <T> ResponseEntity<T> respond(HttpStatus status, IdempotencyService.Result<T> result) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (result.replayed()) {
            builder.header(REPLAYED_HEADER, "true");
        }
        return builder.body(result.body());
    }
}
