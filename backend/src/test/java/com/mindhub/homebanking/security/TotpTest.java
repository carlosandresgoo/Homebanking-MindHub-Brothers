package com.mindhub.homebanking.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B secret for SHA-1. */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfc6238TestVectors() {
        // The RFC lists 8 digits; authenticator apps use the last 6.
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void acceptsOneStepOfDriftAndRejectsReuse() {
        Instant now = Instant.ofEpochSecond(1111111109);
        long step = Totp.stepAt(now);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, null)).isEmpty();
        // Already used (or older than the last used step): replay rejected.
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, step)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "12345", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, null, now, null)).isEmpty();
    }

    @Test
    void base32RoundTripsAndBuildsAnOtpauthUri() {
        byte[] secret = Totp.newSecret();
        assertThat(Totp.fromBase32(Totp.base32(secret))).isEqualTo(secret);
        assertThat(Totp.base32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.otpauthUri("MindHub Brothers", "melba@test.com", RFC_SECRET)).isEqualTo(
                "otpauth://totp/MindHub%20Brothers:melba%40test.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
                        + "&issuer=MindHub%20Brothers&algorithm=SHA1&digits=6&period=30");
    }
}
