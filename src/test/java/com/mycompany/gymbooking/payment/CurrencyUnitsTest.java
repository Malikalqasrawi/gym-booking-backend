package com.mycompany.gymbooking.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Stripe amounts are integers in the currency's minor unit; JOD has 3 decimals (fils). */
class CurrencyUnitsTest {

    @Test
    @DisplayName("JOD has 3 decimals: 20.000 → 20000 fils")
    void dinarsToFils() {
        assertEquals(20000L, CurrencyUnits.toMinorUnits(new BigDecimal("20.000"), "JOD"));
        assertEquals(13500L, CurrencyUnits.toMinorUnits(new BigDecimal("13.5"), "JOD"));
        assertEquals(12750L, CurrencyUnits.toMinorUnits(new BigDecimal("12.750"), "jod"));
    }

    @Test
    @DisplayName("Stripe only takes dinar amounts in steps of 0.010")
    void dinarsMustEndInZero() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyUnits.toMinorUnits(new BigDecimal("13.125"), "JOD"));
    }

    @Test
    @DisplayName("2-decimal and 0-decimal currencies")
    void otherCurrencies() {
        assertEquals(1099L, CurrencyUnits.toMinorUnits(new BigDecimal("10.99"), "USD"));
        assertEquals(500L, CurrencyUnits.toMinorUnits(new BigDecimal("500"), "JPY"));
        assertThrows(IllegalArgumentException.class, () -> CurrencyUnits.toMinorUnits(new BigDecimal("10.999"), "USD"));
    }

    @Test
    @DisplayName("and back: 20000 fils → 20.000 JOD")
    void back() {
        assertEquals(new BigDecimal("20.000"), CurrencyUnits.fromMinorUnits(20000, "JOD"));
        assertEquals(0, CurrencyUnits.fromMinorUnits(13500, "JOD").compareTo(new BigDecimal("13.5")));
    }
}
