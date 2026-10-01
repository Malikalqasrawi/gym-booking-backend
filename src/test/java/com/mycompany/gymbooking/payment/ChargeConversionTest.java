package com.mycompany.gymbooking.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChargeConversionTest {

    private final ChargeConversion usd = new ChargeConversion("usd", new BigDecimal("1.41044"));

    @Test
    @DisplayName("JOD prices are charged in USD at the fixed peg, rounded to the cent")
    void convertsToUsd() {
        assertEquals("USD", usd.currency());
        assertEquals(new BigDecimal("28.21"), usd.toChargeAmount(new BigDecimal("20.000")));
        assertEquals(new BigDecimal("31.73"), usd.toChargeAmount(new BigDecimal("22.500")));
        assertEquals(new BigDecimal("46.54"), usd.toChargeAmount(new BigDecimal("33.000")));
    }

    @Test
    @DisplayName("charging in JOD keeps the price unchanged")
    void jodPassesThrough() {
        ChargeConversion jod = new ChargeConversion("JOD", BigDecimal.ONE);
        assertEquals(new BigDecimal("20.000"), jod.toChargeAmount(new BigDecimal("20.000")));
    }

    @Test
    @DisplayName("invalid settings fail at startup")
    void rejectsInvalidSettings() {
        assertThrows(IllegalStateException.class, () -> new ChargeConversion("US", BigDecimal.ONE));
        assertThrows(IllegalStateException.class, () -> new ChargeConversion("USD", BigDecimal.ZERO));
        assertThrows(IllegalStateException.class, () -> new ChargeConversion("JOD", new BigDecimal("1.41044")));
    }
}
