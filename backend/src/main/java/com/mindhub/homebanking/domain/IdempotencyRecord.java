package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** The stored outcome of a request made with an Idempotency-Key (unique per owner). */
@Entity
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String owner;

    @Column(name = "idem_key", nullable = false, length = 100)
    private String key;

    @Column(nullable = false, length = 50)
    private String operation;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private int responseStatus;

    @Column(nullable = false, length = 4000)
    private String responseBody;

    @Column(nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String owner, String key, String operation, String requestHash, int responseStatus,
                             String responseBody, Instant createdAt) {
        this.owner = owner;
        this.key = key;
        this.operation = operation;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public String getOperation() {
        return operation;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
