package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.ContactDTO;
import com.mindhub.homebanking.dto.CreateContactRequest;
import com.mindhub.homebanking.dto.RenameContactRequest;
import com.mindhub.homebanking.dto.TwoFactorCodeRequest;
import com.mindhub.homebanking.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The caller's saved recipients (agenda). */
@RestController
@RequestMapping("/api/clients/current/contacts")
@PreAuthorize("hasRole('CLIENT')")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public List<ContactDTO> getMine(Authentication authentication) {
        return contactService.findMine(authentication.getName());
    }

    /** 201; 404 unknown account; 422 own account or agenda full; 409 duplicate account or alias; 429 too many. */
    @PostMapping
    public ResponseEntity<ContactDTO> add(@Valid @RequestBody CreateContactRequest request,
                                          Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contactService.add(authentication.getName(), request.accountNumber(), request.alias()));
    }

    @PatchMapping("/{id}")
    public ContactDTO rename(@PathVariable Long id, @Valid @RequestBody RenameContactRequest request,
                             Authentication authentication) {
        return contactService.rename(authentication.getName(), id, request.alias());
    }

    /** 403 (secondFactor) for a wrong code; 422 TWO_FACTOR_REQUIRED without 2FA. */
    @PostMapping("/{id}/trust")
    public ContactDTO trust(@PathVariable Long id, @Valid @RequestBody TwoFactorCodeRequest request,
                            Authentication authentication) {
        return contactService.trust(authentication.getName(), id, request.code());
    }

    @DeleteMapping("/{id}/trust")
    public ContactDTO untrust(@PathVariable Long id, Authentication authentication) {
        return contactService.untrust(authentication.getName(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        contactService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
