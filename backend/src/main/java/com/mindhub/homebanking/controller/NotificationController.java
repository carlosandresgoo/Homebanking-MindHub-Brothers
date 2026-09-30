package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.AlertSettingsDTO;
import com.mindhub.homebanking.dto.NotificationDTO;
import com.mindhub.homebanking.dto.PageDTO;
import com.mindhub.homebanking.dto.UnreadCountDTO;
import com.mindhub.homebanking.service.InboxService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The bell (any signed-in user: admins get sign-in alerts too) and the client's alert settings. */
@RestController
public class NotificationController {

    private static final String BASE = "/api/clients/current/notifications";

    private final InboxService inbox;

    public NotificationController(InboxService inbox) {
        this.inbox = inbox;
    }

    @GetMapping(BASE)
    @PreAuthorize("isAuthenticated()")
    public PageDTO<NotificationDTO> page(@RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "10") @Min(1) @Max(InboxService.MAX_PAGE_SIZE) int size,
                                         Authentication authentication) {
        return inbox.page(authentication.getName(), page, size);
    }

    @GetMapping(BASE + "/unread-count")
    @PreAuthorize("isAuthenticated()")
    public UnreadCountDTO unread(Authentication authentication) {
        return new UnreadCountDTO(inbox.unread(authentication.getName()));
    }

    /** 204; 404 if it is not the caller's. */
    @PostMapping(BASE + "/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(@PathVariable Long id, Authentication authentication) {
        inbox.markRead(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(BASE + "/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        inbox.markAllRead(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/clients/current/alerts")
    @PreAuthorize("hasRole('CLIENT')")
    public AlertSettingsDTO alerts(Authentication authentication) {
        return inbox.alerts(authentication.getName());
    }

    @PutMapping("/api/clients/current/alerts")
    @PreAuthorize("hasRole('CLIENT')")
    public AlertSettingsDTO updateAlerts(@Valid @RequestBody AlertSettingsDTO settings,
                                         Authentication authentication) {
        return inbox.updateAlerts(authentication.getName(), settings);
    }
}
