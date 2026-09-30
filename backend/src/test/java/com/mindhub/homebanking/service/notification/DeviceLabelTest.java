package com.mindhub.homebanking.service.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceLabelTest {

    @Test
    void recognisesCommonBrowsersAndSystems() {
        assertThat(DeviceLabel.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/140.0 Safari/537.36 Edg/140.0")).isEqualTo("Edge en Windows");
        assertThat(DeviceLabel.of("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/140.0 Mobile Safari/537.36")).isEqualTo("Chrome en Android");
        assertThat(DeviceLabel.of("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1")).isEqualTo("Safari en iOS");
        assertThat(DeviceLabel.of("Mozilla/5.0 (Macintosh; Intel Mac OS X 14.0; rv:130.0) Gecko/20100101 "
                + "Firefox/130.0")).isEqualTo("Firefox en macOS");
    }

    @Test
    void otherClientsAndMissingHeaders() {
        assertThat(DeviceLabel.of("curl/8.9.1")).isEqualTo("otra aplicación");
        assertThat(DeviceLabel.of(null)).isEqualTo("un dispositivo desconocido");
        assertThat(DeviceLabel.of(" ")).isEqualTo("un dispositivo desconocido");
    }
}
