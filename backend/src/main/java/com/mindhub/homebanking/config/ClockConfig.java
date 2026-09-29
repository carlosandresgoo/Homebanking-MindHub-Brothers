package com.mindhub.homebanking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class ClockConfig {

    /** Injected instead of calling {@code Instant.now()} so token expiry can be tested. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
