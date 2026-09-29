package com.mindhub.homebanking.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Beans shared by every integration test (one cached Spring context). */
@TestConfiguration
public class TestBeans {

    @Bean
    @Primary
    CapturingMailer capturingMailer() {
        return new CapturingMailer();
    }
}
