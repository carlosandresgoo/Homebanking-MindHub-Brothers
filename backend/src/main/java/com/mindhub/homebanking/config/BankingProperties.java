package com.mindhub.homebanking.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
public record BankingProperties(@NotNull ZoneId zone, @Valid @NotNull Transfers transfers) {

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
