package com.mindhub.homebanking.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** 16-digit, Luhn-valid card numbers and CVVs from a CSPRNG. */
@Component
public class CardNumberGenerator {

    /** Issuer prefix used for every card of the bank. */
    static final String BIN = "450799";

    private final SecureRandom random = new SecureRandom();

    public String number() {
        StringBuilder digits = new StringBuilder(BIN);
        while (digits.length() < 15) {
            digits.append(random.nextInt(10));
        }
        digits.append(luhnCheckDigit(digits));
        return digits.toString();
    }

    public String cvv() {
        return String.format("%03d", random.nextInt(1000));
    }

    public static String hash(String number) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(number.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean isLuhnValid(String number) {
        return luhnCheckDigit(number.substring(0, number.length() - 1)) == number.charAt(number.length() - 1) - '0';
    }

    private static int luhnCheckDigit(CharSequence payload) {
        int sum = 0;
        boolean doubleIt = true; // rightmost payload digit is doubled (the check digit goes after it)
        for (int i = payload.length() - 1; i >= 0; i--) {
            int d = payload.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }
}
