package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.ClientLoanDTO;
import com.mindhub.homebanking.dto.LoanApplicationRequest;
import com.mindhub.homebanking.dto.LoanDTO;
import com.mindhub.homebanking.dto.LoanPaymentRequest;
import com.mindhub.homebanking.service.IdempotencyService;
import com.mindhub.homebanking.service.LoanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LoanController {

    private final LoanService loanService;
    private final IdempotencyService idempotency;

    public LoanController(LoanService loanService, IdempotencyService idempotency) {
        this.loanService = loanService;
        this.idempotency = idempotency;
    }

    /** The loan catalog. */
    @GetMapping("/api/loans")
    @PreAuthorize("isAuthenticated()")
    public List<LoanDTO> getCatalog() {
        return loanService.catalog();
    }

    @GetMapping("/api/clients/current/loans")
    @PreAuthorize("hasRole('CLIENT')")
    public List<ClientLoanDTO> getMyLoans(Authentication authentication) {
        return loanService.findMine(authentication.getName());
    }

    /** 201; 404 unknown loan/account not mine; 409 active loan of the same type; 422 amount/installments. */
    @PostMapping("/api/loans")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ClientLoanDTO> apply(
            @Valid @RequestBody LoanApplicationRequest request,
            @RequestHeader(name = Idempotency.HEADER, required = false)
            @Pattern(regexp = Idempotency.KEY_PATTERN) String idempotencyKey,
            Authentication authentication) {
        String email = authentication.getName();
        return Idempotency.respond(HttpStatus.CREATED, idempotency.execute(email, idempotencyKey, "LOAN_APPLY",
                request, HttpStatus.CREATED.value(), ClientLoanDTO.class, () -> loanService.apply(email, request)));
    }

    /**
     * Pays the next installment; 404 if the loan or account is not the caller's; 422 paid off / no funds.
     * With an {@code Idempotency-Key}, a retry does not pay a second installment.
     */
    @PostMapping("/api/clients/current/loans/{id}/payments")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ClientLoanDTO> payInstallment(
            @PathVariable Long id, @Valid @RequestBody LoanPaymentRequest request,
            @RequestHeader(name = Idempotency.HEADER, required = false)
            @Pattern(regexp = Idempotency.KEY_PATTERN) String idempotencyKey,
            Authentication authentication) {
        String email = authentication.getName();
        return Idempotency.respond(HttpStatus.OK, idempotency.execute(email, idempotencyKey, "LOAN_PAYMENT:" + id,
                request, HttpStatus.OK.value(), ClientLoanDTO.class,
                () -> loanService.payInstallment(id, email, request.accountNumber())));
    }
}
