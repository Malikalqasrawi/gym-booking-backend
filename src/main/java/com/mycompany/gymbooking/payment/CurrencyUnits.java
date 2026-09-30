package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/** Converts between decimal amounts and Stripe's integer minor units (cents, fils, ...). */
final class CurrencyUnits {

    /** Stripe requires three-decimal amounts to end in 0 (multiples of 0.010). */
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

    /** Throws instead of rounding when the amount has more decimals than the currency allows. */
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

    static BigDecimal fromMinorUnits(long minor, String currency) {
        return BigDecimal.valueOf(minor, decimals(currency));
    }
}
