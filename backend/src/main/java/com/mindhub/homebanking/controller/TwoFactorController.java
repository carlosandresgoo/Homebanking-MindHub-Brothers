package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.dto.DisableTwoFactorRequest;
import com.mindhub.homebanking.dto.TwoFactorCodeRequest;
import com.mindhub.homebanking.dto.TwoFactorSetupDTO;
import com.mindhub.homebanking.service.TwoFactorService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Authenticator-app second factor (TOTP) for clients, and its admin reset. */
@RestController
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    public TwoFactorController(TwoFactorService twoFactorService) {
        this.twoFactorService = twoFactorService;
    }

    /** Step 1: a new secret to scan (not active until confirmed). 409 if already enabled. */
    @PostMapping("/api/clients/current/2fa/setup")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<TwoFactorSetupDTO> setup(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(twoFactorService.setup(authentication.getName()));
    }

    /** Step 2: confirm with a code from the app. 403 (secondFactor=INVALID) for a wrong code. */
    @PostMapping("/api/clients/current/2fa/enable")
    @PreAuthorize("hasRole('CLIENT')")
    public ClientDTO enable(@Valid @RequestBody TwoFactorCodeRequest request, Authentication authentication) {
        return twoFactorService.enable(authentication.getName(), request.code());
    }

    /** Requires the password (422 if wrong) and a current code (403 if wrong). */
    @PostMapping("/api/clients/current/2fa/disable")
    @PreAuthorize("hasRole('CLIENT')")
    public ClientDTO disable(@Valid @RequestBody DisableTwoFactorRequest request, Authentication authentication) {
        return twoFactorService.disable(authentication.getName(), request.password(), request.code());
    }

    /** For a client who lost their phone: the admin turns 2FA off so they can enroll again. */
    @DeleteMapping("/api/clients/{id}/2fa")
    @PreAuthorize("hasRole('ADMIN')")
    public ClientDTO reset(@PathVariable Long id) {
        return twoFactorService.reset(id);
    }
}
