package com.mindhub.homebanking.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Alias of an account (like "sol.rio.mate"): what people share instead of the CBU. Same rules as the
 * BCRA's: 6 to 20 characters, letters without accents, digits, dots and hyphens. Stored lower-case and
 * compared ignoring case. Aliases that look like an account number ("vin-…") are reserved, so a
 * recipient key is never ambiguous.
 */
public final class AccountAlias {

    public static final String PATTERN = "^[A-Za-z0-9.-]{6,20}$";
    public static final String MESSAGE = "must be 6 to 20 letters (without accents), digits, dots or hyphens";

    private static final Pattern RESERVED = Pattern.compile("^vin-?\\d*$");

    private AccountAlias() {
    }

    public static String normalize(String alias) {
        return alias.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isReserved(String normalized) {
        return RESERVED.matcher(normalized).matches();
    }
}
