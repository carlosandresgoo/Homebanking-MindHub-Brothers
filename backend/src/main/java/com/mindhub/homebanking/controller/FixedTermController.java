package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.CreateFixedTermRequest;
import com.mindhub.homebanking.dto.FixedTermDTO;
import com.mindhub.homebanking.dto.FixedTermPlanDTO;
import com.mindhub.homebanking.dto.UpdateFixedTermRequest;
import com.mindhub.homebanking.service.FixedTermService;
import com.mindhub.homebanking.service.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FixedTermController {

    private final FixedTermService fixedTermService;
    private final IdempotencyService idempotency;

    public FixedTermController(FixedTermService fixedTermService, IdempotencyService idempotency) {
        this.fixedTermService = fixedTermService;
        this.idempotency = idempotency;
    }

    /** Available terms and their annual rates. */
    @GetMapping("/api/fixed-terms/plans")
    @PreAuthorize("isAuthenticated()")
    public List<FixedTermPlanDTO> plans() {
        return fixedTermService.plans();
    }

    @GetMapping("/api/clients/current/fixed-terms")
    @PreAuthorize("hasRole('CLIENT')")
    public List<FixedTermDTO> getMine(Authentication authentication) {
        return fixedTermService.findMine(authentication.getName());
    }

    /** 201; 404 account not mine; 422 minimum, term or funds. A retried Idempotency-Key never debits twice. */
    @PostMapping("/api/clients/current/fixed-terms")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<FixedTermDTO> create(
            @Valid @RequestBody CreateFixedTermRequest request,
            @RequestHeader(name = Idempotency.HEADER, required = false)
            @Pattern(regexp = Idempotency.KEY_PATTERN) String idempotencyKey,
            Authentication authentication) {
        String email = authentication.getName();
        return Idempotency.respond(HttpStatus.CREATED, idempotency.execute(email, idempotencyKey,
                "FIXED_TERM_CREATE", request, HttpStatus.CREATED.value(), FixedTermDTO.class,
                () -> fixedTermService.create(email, request)));
    }

    /** Turns automatic renewal on or off; 422 once paid. */
    @PatchMapping("/api/clients/current/fixed-terms/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public FixedTermDTO update(@PathVariable Long id, @Valid @RequestBody UpdateFixedTermRequest request,
                               Authentication authentication) {
        return fixedTermService.setAutoRenew(authentication.getName(), id, request.autoRenew());
    }
}
