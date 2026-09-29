package com.mindhub.homebanking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class ClockConfig {

    /**
     * Injected instead of calling {@code Instant.now()} so time-based rules can be tested. Its zone is
     * the bank's, so movement dates ({@code LocalDateTime.now(clock)}) and "today" are local time.
     */
    @Bean
    Clock clock(BankingProperties properties) {
        return Clock.system(properties.zone());
    }
}
