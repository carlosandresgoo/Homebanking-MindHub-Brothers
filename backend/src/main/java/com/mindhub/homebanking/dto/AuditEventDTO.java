package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.AuditAction;

import java.time.Instant;

public record AuditEventDTO(Long id, Instant occurredAt, String actor, String actorRole, AuditAction action,
                            String target, String outcome, String ip, String details) {
}
