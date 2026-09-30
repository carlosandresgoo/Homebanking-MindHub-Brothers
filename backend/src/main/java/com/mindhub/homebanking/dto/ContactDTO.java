package com.mindhub.homebanking.dto;

import java.time.LocalDateTime;

/**
 * @param holderDisplay masked holder name, e.g. "Lucía P."
 * @param trusted       large transfers to it need no 2FA code
 */
public record ContactDTO(Long id, String alias, String accountNumber, String holderDisplay, LocalDateTime createdAt,
                         boolean trusted) {
}
