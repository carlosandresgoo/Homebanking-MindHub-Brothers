package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.TransferReceiptDTO;
import com.mindhub.homebanking.dto.TransferRequest;
import com.mindhub.homebanking.service.IdempotencyService;
import com.mindhub.homebanking.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

    private final TransferService transferService;
    private final IdempotencyService idempotency;

    public TransferController(TransferService transferService, IdempotencyService idempotency) {
        this.transferService = transferService;
        this.idempotency = idempotency;
    }

    /**
     * 201 with a receipt; 404 when the source is not the caller's (or does not exist) or the destination
     * does not exist; 422 for same account or insufficient funds; 400 for invalid input. With an
     * {@code Idempotency-Key}, a retry returns the original receipt instead of transferring again.
     */
    @PostMapping("/api/transfers")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<TransferReceiptDTO> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(name = Idempotency.HEADER, required = false)
            @Pattern(regexp = Idempotency.KEY_PATTERN) String idempotencyKey,
            Authentication authentication) {
        String email = authentication.getName();
        return Idempotency.respond(HttpStatus.CREATED, idempotency.execute(email, idempotencyKey, "TRANSFER",
                request, HttpStatus.CREATED.value(), TransferReceiptDTO.class,
                () -> transferService.transfer(email, request)));
    }
}
