package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Cbu;
import com.mindhub.homebanking.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Identifiers of new accounts, all unique and unpredictable: the number ({@code VIN-48213907}), the CBU
 * (random account part, see {@link Cbu}) and a three-word alias ({@code sol.rio.mate}).
 */
@Component
public class AccountNumberGenerator {

    private static final int DIGITS = 8;

    /** Short, neutral words without accents or ñ (alias rules): three of them never exceed 20 characters. */
    private static final List<String> WORDS = List.of(
            "sol", "luna", "rio", "mar", "monte", "valle", "nube", "lago", "bosque", "cielo",
            "mate", "tango", "ceibo", "pampa", "puma", "condor", "zorro", "loro", "tordo", "tero",
            "piedra", "arena", "roble", "pino", "olivo", "trigo", "maiz", "limon", "menta", "canela",
            "faro", "puente", "barco", "vela", "ancla", "isla", "costa", "brisa", "trueno", "lluvia",
            "verde", "azul", "rojo", "dorado", "blanco", "negro", "claro", "suave", "firme", "libre");

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** A new, empty account with fresh identifiers; the caller adds it to its client and saves it. */
    public Account newAccount(LocalDateTime creationDate) {
        return newAccount(next(), creationDate);
    }

    /** Same, with a given number (seed data). */
    public Account newAccount(String number, LocalDateTime creationDate) {
        return new Account(number, nextCbu(), nextAlias(), creationDate, BigDecimal.ZERO);
    }

    public String next() {
        String number;
        do {
            number = "VIN-" + String.format("%0" + DIGITS + "d", random.nextInt(100_000_000));
        } while (accountRepository.existsByNumber(number));
        return number;
    }

    String nextCbu() {
        String cbu;
        do {
            cbu = Cbu.of(String.format("%013d", random.nextLong(10_000_000_000_000L)));
        } while (accountRepository.existsByCbu(cbu));
        return cbu;
    }

    String nextAlias() {
        String alias;
        do {
            alias = word() + "." + word() + "." + word();
        } while (accountRepository.existsByAliasIgnoreCase(alias));
        return alias;
    }

    private String word() {
        return WORDS.get(random.nextInt(WORDS.size()));
    }
}
