package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.CreateScheduledTransferRequest;
import com.mindhub.homebanking.dto.ScheduledTransferDTO;
import com.mindhub.homebanking.service.IdempotencyService;
import com.mindhub.homebanking.service.ScheduledTransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clients/current/scheduled-transfers")
@PreAuthorize("hasRole('CLIENT')")
public class ScheduledTransferController {

    private final ScheduledTransferService service;
    private final IdempotencyService idempotency;

    public ScheduledTransferController(ScheduledTransferService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @GetMapping
    public List<ScheduledTransferDTO> getMine(Authentication authentication) {
        return service.findMine(authentication.getName());
    }

    /** 201; a retried Idempotency-Key never schedules twice. */
    @PostMapping
    public ResponseEntity<ScheduledTransferDTO> create(
            @Valid @RequestBody CreateScheduledTransferRequest request,
            @RequestHeader(name = Idempotency.HEADER, required = false)
            @Pattern(regexp = Idempotency.KEY_PATTERN) String idempotencyKey,
            Authentication authentication) {
        String email = authentication.getName();
        return Idempotency.respond(HttpStatus.CREATED, idempotency.execute(email, idempotencyKey,
                "SCHEDULED_TRANSFER_CREATE", request, HttpStatus.CREATED.value(), ScheduledTransferDTO.class,
                () -> service.create(email, request)));
    }

    @PostMapping("/{id}/pause")
    public ScheduledTransferDTO pause(@PathVariable Long id, Authentication authentication) {
        return service.pause(authentication.getName(), id);
    }

    @PostMapping("/{id}/resume")
    public ScheduledTransferDTO resume(@PathVariable Long id, Authentication authentication) {
        return service.resume(authentication.getName(), id);
    }

    /** Cancels it (kept in the history). */
    @DeleteMapping("/{id}")
    public ScheduledTransferDTO cancel(@PathVariable Long id, Authentication authentication) {
        return service.cancel(authentication.getName(), id);
    }
}
