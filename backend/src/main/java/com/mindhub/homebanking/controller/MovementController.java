package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.MovementFilter;
import com.mindhub.homebanking.dto.MovementReceiptDTO;
import com.mindhub.homebanking.dto.PageDTO;
import com.mindhub.homebanking.dto.TransactionDTO;
import com.mindhub.homebanking.service.MovementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Movements of an account (owner or ADMIN; 404 otherwise). */
@RestController
@PreAuthorize("isAuthenticated()")
public class MovementController {

    private final MovementService movementService;

    public MovementController(MovementService movementService) {
        this.movementService = movementService;
    }

    /** Newest first. Filters: from/to (yyyy-MM-dd, inclusive), type, category, q (description text). */
    @GetMapping("/api/accounts/{id}/transactions")
    public PageDTO<TransactionDTO> page(@PathVariable Long id, @Valid @ModelAttribute MovementFilter filter,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(MovementService.MAX_PAGE_SIZE) int size,
                                        Authentication authentication) {
        return movementService.page(id, authentication.getName(), isAdmin(authentication), filter, page, size);
    }

    /** Same filters as the list, as a CSV attachment (semicolon-separated, UTF-8 with BOM). */
    @GetMapping("/api/accounts/{id}/transactions/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @Valid @ModelAttribute MovementFilter filter,
                                         Authentication authentication) {
        MovementService.CsvExport export = movementService.exportCsv(id, authentication.getName(),
                isAdmin(authentication), filter);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.filename()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(export.content());
    }

    @GetMapping("/api/transactions/{id}")
    public MovementReceiptDTO receipt(@PathVariable Long id, Authentication authentication) {
        return movementService.receipt(id, authentication.getName(), isAdmin(authentication));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
