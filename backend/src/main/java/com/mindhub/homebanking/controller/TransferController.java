package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.TransferReceiptDTO;
import com.mindhub.homebanking.dto.TransferRequest;
import com.mindhub.homebanking.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * 201 with a receipt; 404 when the source is not the caller's (or does not exist) or the destination
     * does not exist; 422 for same account or insufficient funds; 400 for invalid input.
     */
    @PostMapping("/api/transfers")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<TransferReceiptDTO> transfer(@Valid @RequestBody TransferRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.transfer(authentication.getName(), request));
    }
}
