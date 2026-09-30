package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/**
 * Stripe wants amounts as whole numbers in the currency's SMALLEST unit:
 *
 *   20.00 USD  → 2000   (2 decimals: cents)
 *   20.000 JOD → 20000  (3 decimals: fils)
 *   2000 JPY   → 2000   (no decimals)
 *
 * Whole numbers avoid rounding mistakes like 0.1 + 0.2 = 0.30000000000000004.
 */
final class CurrencyUnits {

    /** Dinars and similar. Stripe also needs the last digit to be 0 (steps of 0.010). */
    private static final Set<String> THREE_DECIMALS = Set.of("BHD", "JOD", "KWD", "OMR", "TND");

    private static final Set<String> ZERO_DECIMALS = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF", "VND", "VUV", "XAF", "XOF", "XPF");

    private CurrencyUnits() {
    }

    static int decimals(String currency) {
        String code = currency.toUpperCase(Locale.ROOT);
        if (THREE_DECIMALS.contains(code)) {
            return 3;
        }
        return ZERO_DECIMALS.contains(code) ? 0 : 2;
    }

    /** 20.000 JOD → 20000. Refuses amounts that don't fit exactly (instead of silently rounding money). */
    static long toMinorUnits(BigDecimal amount, String currency) {
        int decimals = decimals(currency);
        long minor;
        try {
            minor = amount.setScale(decimals, RoundingMode.UNNECESSARY).movePointRight(decimals).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(amount + " " + currency + " has too many decimals", e);
        }
        if (decimals == 3 && minor % 10 != 0) {
            throw new IllegalArgumentException(amount + " " + currency + ": Stripe only takes steps of 0.010");
        }
        return minor;
    }

    /** 20000 JOD → 20.000 */
    static BigDecimal fromMinorUnits(long minor, String currency) {
        return BigDecimal.valueOf(minor, decimals(currency));
    }
}
