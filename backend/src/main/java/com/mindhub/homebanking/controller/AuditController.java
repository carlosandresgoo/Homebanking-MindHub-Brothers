package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.dto.AuditEventDTO;
import com.mindhub.homebanking.dto.PageDTO;
import com.mindhub.homebanking.service.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Newest first; all filters optional; {@code to} is exclusive; size is capped at 100. */
    @GetMapping("/api/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public PageDTO<AuditEventDTO> search(@RequestParam(required = false) String actor,
                                         @RequestParam(required = false) AuditAction action,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return auditService.search(actor, action, from, to, page, size);
    }
}
