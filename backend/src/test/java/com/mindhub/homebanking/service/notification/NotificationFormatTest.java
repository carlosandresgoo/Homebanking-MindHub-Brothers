package com.mindhub.homebanking.service.notification;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFormatTest {

    /** es-AR puts a (non-breaking) space after "$" and before "%". */
    private static final String SPACE = "[\\s\\u00A0]";

    @Test
    void moneyUsesArgentineSeparators() {
        assertThat(NotificationService.money(new BigDecimal("1234.5"))).matches("\\$" + SPACE + "1\\.234,50");
    }

    @Test
    void ratesAreStoredAsFractionsAndShownAsPercentages() {
        assertThat(NotificationService.percent(new BigDecimal("0.3500"))).matches("35,00" + SPACE + "%");
    }

    @Test
    void onlyTheLastFourCharactersOfAnAccountAreShown() {
        assertThat(NotificationService.mask("VIN-12345678")).isEqualTo("···5678");
        assertThat(NotificationService.mask("VIN1")).isEqualTo("VIN1");
    }
}
