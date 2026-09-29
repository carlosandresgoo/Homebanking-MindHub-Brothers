package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.AuditEvent;
import com.mindhub.homebanking.dto.AuditEventDTO;
import com.mindhub.homebanking.dto.PageDTO;
import com.mindhub.homebanking.repository.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Append-only audit trail. Each event is written in its own transaction ({@code REQUIRES_NEW}), so it
 * is kept even when the audited operation fails and rolls back; a failure to audit never breaks the
 * business operation. Details must never contain passwords, tokens or full card numbers.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository repository;
    private final Clock clock;
    /** Independent transaction (explicit, since a self-call would bypass a @Transactional proxy). */
    private final TransactionTemplate newTransaction;

    public AuditService(AuditEventRepository repository, Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Records a successful operation of the current user. Inside a transaction it is written only
     * after that transaction commits, so a rolled-back operation is never reported as done.
     */
    public void success(AuditAction action, String target, String details) {
        record(currentActor(), currentRole(), action, target, AuditEvent.Outcome.SUCCESS, details);
    }

    /** Records an event for an explicit actor (e.g. a login, before the user is authenticated). */
    public void record(String actor, String actorRole, AuditAction action, String target,
                       AuditEvent.Outcome outcome, String details) {
        // Capture request-bound data now (actor, IP, time), write later if needed.
        AuditEvent event = new AuditEvent(clock.instant(), normalize(actor), actorRole, action, target, outcome,
                currentIp(), details);
        if (outcome == AuditEvent.Outcome.SUCCESS && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(event);
                }
            });
        } else {
            write(event);
        }
    }

    private void write(AuditEvent event) {
        try {
            newTransaction.executeWithoutResult(status -> repository.save(event));
        } catch (RuntimeException e) {
            log.error("Could not write audit event {}", event.getAction(), e);
        }
    }

    @Transactional(readOnly = true)
    public PageDTO<AuditEventDTO> search(String actor, AuditAction action, Instant from, Instant to,
                                         int page, int size) {
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("actor")), "%" + actor.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        Page<AuditEvent> result = repository.findAll(spec, pageable);
        return PageDTO.of(result.map(AuditService::toDto));
    }

    private static AuditEventDTO toDto(AuditEvent e) {
        return new AuditEventDTO(e.getId(), e.getOccurredAt(), e.getActor(), e.getActorRole(), e.getAction(),
                e.getTarget(), e.getOutcome().name(), e.getIp(), e.getDetails());
    }

    private static String normalize(String actor) {
        return actor == null ? null : actor.trim().toLowerCase(Locale.ROOT);
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() ? null : authentication.getName();
    }

    private static String currentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse(null);
    }

    private static String currentIp() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr()
                : null;
    }
}
