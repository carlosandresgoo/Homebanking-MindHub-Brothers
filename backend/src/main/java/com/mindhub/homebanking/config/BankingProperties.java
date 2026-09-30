package com.mindhub.homebanking.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * Business rules that operations may tune without a release.
 *
 * @param zone the bank's time zone: "today" for daily limits starts at midnight here
 */
@Validated
@ConfigurationProperties(prefix = "app.banking")
public record BankingProperties(@NotNull ZoneId zone, @Valid @NotNull Transfers transfers,
                                @Valid @NotNull FixedTerms fixedTerms,
                                @Valid @NotNull ScheduledTransfers scheduledTransfers) {

    /**
     * @param cron    when due scheduled transfers run (bank's time zone); also at start-up
     * @param maxOpen active or paused scheduled transfers per client
     */
    public record ScheduledTransfers(@NotNull String cron, @Min(1) int maxOpen) {
    }

    /**
     * @param minAmount  smallest principal accepted
     * @param payoutCron when matured fixed terms are paid (bank's time zone); they are also paid at
     *                   start-up, so none is missed while the server was down
     */
    public record FixedTerms(@NotNull @DecimalMin("0.01") BigDecimal minAmount, @NotNull String payoutCron) {
    }

    /**
     * Limits for transfers to other clients' accounts (moving money between one's own accounts is free).
     *
     * @param dailyLimit                 per client and day without a second factor enabled
     * @param dailyLimitWithSecondFactor per client and day once 2FA is enabled
     * @param secondFactorThreshold      with 2FA enabled, transfers of this amount or more need a code
     */
    public record Transfers(@NotNull @DecimalMin("0") BigDecimal dailyLimit,
                            @NotNull @DecimalMin("0") BigDecimal dailyLimitWithSecondFactor,
                            @NotNull @DecimalMin("0") BigDecimal secondFactorThreshold) {
    }
}
