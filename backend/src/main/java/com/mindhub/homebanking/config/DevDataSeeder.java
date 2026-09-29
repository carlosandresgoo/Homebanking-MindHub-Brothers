package com.mindhub.homebanking.config;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Role;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Sample data for local development only. Both users share one password taken from
 * {@code DEV_SEED_PASSWORD}; if unset, a random one is generated and printed once.
 */
@Component
@Profile("dev")
class DevDataSeeder implements ApplicationRunner {

    static final String CLIENT_EMAIL = "melba@gmail.com";
    static final String ADMIN_EMAIL = "admin@mindhub.com";

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String configuredPassword;

    DevDataSeeder(ClientRepository clientRepository, AccountRepository accountRepository,
                  PasswordEncoder passwordEncoder, @Value("${DEV_SEED_PASSWORD:}") String configuredPassword) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (clientRepository.count() > 0) {
            return;
        }
        String password = configuredPassword.isBlank() ? randomPassword() : configuredPassword;
        String hash = passwordEncoder.encode(password);

        Client melba = clientRepository.save(new Client("Melba", "Morel", CLIENT_EMAIL, hash, Role.CLIENT));
        Account vin001 = new Account("vin001", LocalDateTime.now(), new BigDecimal("5000.00"));
        Account vin002 = new Account("vin002", LocalDateTime.now().plusDays(1), new BigDecimal("7500.00"));
        melba.addAccount(vin001);
        melba.addAccount(vin002);
        accountRepository.save(vin001);
        accountRepository.save(vin002);

        clientRepository.save(new Client("Admin", "Mindhub", ADMIN_EMAIL, hash, Role.ADMIN));

        if (configuredPassword.isBlank()) {
            log.warn("DEV seed users {} (CLIENT) and {} (ADMIN) created with generated password: {}",
                    CLIENT_EMAIL, ADMIN_EMAIL, password);
        } else {
            log.info("DEV seed users {} (CLIENT) and {} (ADMIN) created with DEV_SEED_PASSWORD",
                    CLIENT_EMAIL, ADMIN_EMAIL);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
