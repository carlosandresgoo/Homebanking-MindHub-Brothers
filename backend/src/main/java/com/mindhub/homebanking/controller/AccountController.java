package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.AccountDTO;
import com.mindhub.homebanking.dto.AccountDetailDTO;
import com.mindhub.homebanking.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/api/clients/current/accounts")
    @PreAuthorize("isAuthenticated()")
    public List<AccountDTO> getMyAccounts(Authentication authentication) {
        return accountService.findMine(authentication.getName());
    }

    @PostMapping("/api/clients/current/accounts")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<AccountDTO> openAccount(Authentication authentication) {
        AccountDTO created = accountService.open(authentication.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/accounts/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Owner or ADMIN; anyone else gets 404. */
    @GetMapping("/api/accounts/{id}")
    @PreAuthorize("isAuthenticated()")
    public AccountDetailDTO getAccount(@PathVariable Long id, Authentication authentication) {
        return accountService.findDetail(id, authentication.getName(), isAdmin(authentication));
    }

    @DeleteMapping("/api/accounts/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> closeAccount(@PathVariable Long id, Authentication authentication) {
        accountService.close(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
