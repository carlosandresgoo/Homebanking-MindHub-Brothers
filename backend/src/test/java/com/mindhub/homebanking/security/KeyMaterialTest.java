package com.mindhub.homebanking.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyMaterialTest {

    @TempDir
    Path dir;

    private static MockEnvironment profile(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return env;
    }

    @Test
    void devKeyIsGeneratedOnceAndReusedAfterRestart() {
        String file = dir.resolve("data/dev.key").toString();

        byte[] first = KeyMaterial.resolve("TEST_KEY", "", 32, file, profile("dev"));
        byte[] second = KeyMaterial.resolve("TEST_KEY", null, 32, file, profile("dev"));

        assertThat(first).hasSize(32).isEqualTo(second);
        assertThat(Files.exists(Path.of(file))).isTrue();
    }

    @Test
    void configuredValueWinsAndMustBeLongEnough() {
        byte[] key = new byte[32];
        key[0] = 7;
        assertThat(KeyMaterial.resolve("TEST_KEY", Base64.getEncoder().encodeToString(key), 32, null, profile("prod")))
                .isEqualTo(key);
        assertThatThrownBy(() -> KeyMaterial.resolve("TEST_KEY", Base64.getEncoder().encodeToString(new byte[8]), 32,
                null, profile("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 256 bits");
    }

    @Test
    void productionWithoutAValueFailsFastAndNeverUsesTheDevFile() {
        String file = dir.resolve("dev.key").toString();
        assertThatThrownBy(() -> KeyMaterial.resolve("TEST_KEY", "", 32, file, profile("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_KEY must be set");
        assertThat(Files.exists(Path.of(file))).isFalse();
    }
}
