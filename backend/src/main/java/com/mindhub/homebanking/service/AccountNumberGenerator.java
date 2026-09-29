package com.mindhub.homebanking.service;

import com.mindhub.homebanking.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Unique, unpredictable account numbers like {@code VIN-48213907}. */
@Component
public class AccountNumberGenerator {

    private static final int DIGITS = 8;

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String next() {
        String number;
        do {
            number = "VIN-" + String.format("%0" + DIGITS + "d", random.nextInt(100_000_000));
        } while (accountRepository.existsByNumber(number));
        return number;
    }
}
