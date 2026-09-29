package com.mindhub.homebanking.dto;

import java.math.BigDecimal;

/**
 * Today's allowance for transfers to other clients.
 *
 * @param secondFactorEnabled   when true, transfers of {@code secondFactorThreshold} or more need a code
 * @param limitWithSecondFactor the daily limit the client would get by enabling 2FA
 */
public record TransferLimitsDTO(BigDecimal dailyLimit, BigDecimal usedToday, BigDecimal remainingToday,
                                boolean secondFactorEnabled, BigDecimal secondFactorThreshold,
                                BigDecimal limitWithSecondFactor) {
}
