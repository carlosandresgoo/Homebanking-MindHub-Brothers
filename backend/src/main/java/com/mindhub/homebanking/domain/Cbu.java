package com.mindhub.homebanking.domain;

/**
 * CBU (Clave Bancaria Uniforme): the 22-digit key of an Argentine bank account.
 * <ul>
 *   <li>Block 1 (8 digits): bank (3) + branch (4) + check digit.</li>
 *   <li>Block 2 (14 digits): account (13) + check digit.</li>
 * </ul>
 * Each check digit is {@code (10 - sum(digit * weight) % 10) % 10}, with weights 7,1,3,9 (block 1) and
 * 3,9,7,1 (block 2) repeated. This bank uses the fictional code 999, so no real institution is mimicked.
 */
public final class Cbu {

    public static final String BANK_CODE = "999";
    static final String BRANCH = "0001";

    private static final int[] BLOCK_1_WEIGHTS = {7, 1, 3, 9, 7, 1, 3};
    private static final int[] BLOCK_2_WEIGHTS = {3, 9, 7, 1, 3, 9, 7, 1, 3, 9, 7, 1, 3};

    /** Bank, branch and check digit: "99900018". */
    public static final String BLOCK_1 = BANK_CODE + BRANCH + checkDigit(BANK_CODE + BRANCH, BLOCK_1_WEIGHTS);

    private Cbu() {
    }

    /** @param accountPart the 13 digits that identify the account inside this bank */
    public static String of(String accountPart) {
        if (accountPart == null || !accountPart.matches("\\d{13}")) {
            throw new IllegalArgumentException("The account part of a CBU has 13 digits");
        }
        return BLOCK_1 + accountPart + checkDigit(accountPart, BLOCK_2_WEIGHTS);
    }

    /** 22 digits with both check digits right (any bank). */
    public static boolean isValid(String cbu) {
        return cbu != null && cbu.matches("\\d{22}")
                && checkDigit(cbu.substring(0, 7), BLOCK_1_WEIGHTS) == cbu.charAt(7) - '0'
                && checkDigit(cbu.substring(8, 21), BLOCK_2_WEIGHTS) == cbu.charAt(21) - '0';
    }

    /** Whether a (valid) CBU belongs to this bank. */
    public static boolean isOwnBank(String cbu) {
        return cbu.startsWith(BANK_CODE);
    }

    private static int checkDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        return (10 - sum % 10) % 10;
    }
}
