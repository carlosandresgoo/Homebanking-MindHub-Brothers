package com.mindhub.homebanking.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbuTest {

    /** A publicly circulated sample CBU of another bank (285 = Banco Macro). */
    private static final String OTHER_BANK_SAMPLE = "2850590940090418135201";

    @Test
    void checkDigitsFollowTheStandardAlgorithm() {
        assertThat(Cbu.isValid(OTHER_BANK_SAMPLE)).isTrue();
        assertThat(Cbu.isValid("2850590940090418135202")).as("second check digit altered").isFalse();
        assertThat(Cbu.isValid("2850591940090418135201")).as("first check digit altered").isFalse();
        assertThat(Cbu.isValid("285059094009041813520")).as("21 digits").isFalse();
        assertThat(Cbu.isValid("28505909400904181352O1")).as("a letter").isFalse();
        assertThat(Cbu.isValid(null)).isFalse();
    }

    @Test
    void ourCbusCarryTheFictionalBankCodeAndAreValid() {
        assertThat(Cbu.BLOCK_1).isEqualTo("99900018");
        String cbu = Cbu.of("0000000000001");
        assertThat(cbu).isEqualTo("9990001800000000000017").hasSize(22);
        assertThat(Cbu.isValid(cbu)).isTrue();
        assertThat(Cbu.isOwnBank(cbu)).isTrue();
        assertThat(Cbu.isOwnBank(OTHER_BANK_SAMPLE)).isFalse();
    }

    @Test
    void theAccountPartMustHaveThirteenDigits() {
        assertThatThrownBy(() -> Cbu.of("123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cbu.of("000000000000A")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aliasesThatLookLikeAccountNumbersAreReserved() {
        assertThat(AccountAlias.isReserved("vin-12345678")).isTrue();
        assertThat(AccountAlias.isReserved("vin001")).isTrue();
        assertThat(AccountAlias.isReserved("vin001.test")).isFalse();
        assertThat(AccountAlias.normalize("  Sol.Rio.MATE ")).isEqualTo("sol.rio.mate");
    }
}
