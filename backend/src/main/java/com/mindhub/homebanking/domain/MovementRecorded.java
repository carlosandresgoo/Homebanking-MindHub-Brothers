package com.mindhub.homebanking.domain;

/**
 * Published by Spring Data when a {@link Transaction} is saved (see {@link Transaction}), inside the
 * transaction that moved the money. Every balance change goes through {@link Account#credit} or
 * {@link Account#debit} and is saved, so listeners see all of them without each service calling them.
 */
public record MovementRecorded(Transaction transaction) {
}
