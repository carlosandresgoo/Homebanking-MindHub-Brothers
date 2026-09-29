package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.dto.FinancialSummaryDTO;
import com.mindhub.homebanking.service.SummaryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /** Income vs expenses per month, spending by category and the daily balance, for the dashboard. */
    @GetMapping("/api/clients/current/summary")
    @PreAuthorize("hasRole('CLIENT')")
    public FinancialSummaryDTO summary(
            @RequestParam(defaultValue = "6") @Min(1) @Max(SummaryService.MAX_MONTHS) int months,
            Authentication authentication) {
        return summaryService.summary(authentication.getName(), months);
    }
}
