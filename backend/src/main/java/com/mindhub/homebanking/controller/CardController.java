package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.CardDTO;
import com.mindhub.homebanking.dto.IssueCardRequest;
import com.mindhub.homebanking.dto.IssuedCardDTO;
import com.mindhub.homebanking.service.CardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/api/clients/current/cards")
    @PreAuthorize("hasRole('CLIENT')")
    public List<CardDTO> getMyCards(Authentication authentication) {
        return cardService.findMine(authentication.getName());
    }

    /** Returns the full number and CVV once; the response must not be cached. */
    @PostMapping("/api/clients/current/cards")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<IssuedCardDTO> issueCard(@Valid @RequestBody IssueCardRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(cardService.issue(authentication.getName(), request));
    }

    @DeleteMapping("/api/cards/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> deactivateCard(@PathVariable Long id, Authentication authentication) {
        cardService.deactivate(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
