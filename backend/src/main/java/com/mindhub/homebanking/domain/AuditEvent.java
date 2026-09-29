package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Immutable audit record: created once, never updated (no setters). */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    public enum Outcome { SUCCESS, FAILURE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(updatable = false)
    private String actor;

    @Column(length = 20, updatable = false)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private AuditAction action;

    @Column(length = 120, updatable = false)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Outcome outcome;

    @Column(length = 45, updatable = false)
    private String ip;

    @Column(length = 500, updatable = false)
    private String details;

    protected AuditEvent() {
    }

    public AuditEvent(Instant occurredAt, String actor, String actorRole, AuditAction action, String target,
                      Outcome outcome, String ip, String details) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.actorRole = actorRole;
        this.action = action;
        this.target = truncate(target, 120);
        this.outcome = outcome;
        this.ip = truncate(ip, 45);
        this.details = truncate(details, 500);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getActorRole() {
        return actorRole;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getIp() {
        return ip;
    }

    public String getDetails() {
        return details;
    }
}
