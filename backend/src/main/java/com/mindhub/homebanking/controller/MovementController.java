package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.MovementFilter;
import com.mindhub.homebanking.dto.MovementReceiptDTO;
import com.mindhub.homebanking.dto.PageDTO;
import com.mindhub.homebanking.dto.TransactionDTO;
import com.mindhub.homebanking.service.MovementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Movements of an account (owner or ADMIN; 404 otherwise). */
@RestController
@PreAuthorize("isAuthenticated()")
public class MovementController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

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

    /**
     * Same filters as the list, as an attachment: {@code format=csv} (default; semicolon-separated,
     * UTF-8 with BOM) or {@code format=xlsx} (Excel).
     */
    @GetMapping("/api/accounts/{id}/transactions/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @Valid @ModelAttribute MovementFilter filter,
                                         @RequestParam(defaultValue = "csv") @Pattern(regexp = "csv|xlsx") String format,
                                         Authentication authentication) {
        boolean xlsx = format.equals("xlsx");
        MovementService.FileExport export = xlsx
                ? movementService.exportXlsx(id, authentication.getName(), isAdmin(authentication), filter)
                : movementService.exportCsv(id, authentication.getName(), isAdmin(authentication), filter);
        return attachment(export, xlsx ? XLSX : new MediaType("text", "csv", StandardCharsets.UTF_8));
    }

    /** PDF statement for {@code from}..{@code to} (yyyy-MM-dd, inclusive; default: this month so far). */
    @GetMapping("/api/accounts/{id}/statement")
    public ResponseEntity<byte[]> statement(@PathVariable Long id,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            Authentication authentication) {
        return attachment(movementService.statement(id, authentication.getName(), isAdmin(authentication), from, to),
                MediaType.APPLICATION_PDF);
    }

    private static ResponseEntity<byte[]> attachment(MovementService.FileExport export, MediaType type) {
        return ResponseEntity.ok()
                .contentType(type)
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
