package com.mycompany.gymbooking.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Phone numbers checked against each country's rules (Google's libphonenumber). */
class PhoneNumbersTest {

    @Test
    @DisplayName("Jordanian mobile numbers, typed in any common way, become +962...")
    void jordanianMobiles() {
        for (String typed : List.of("0791234567", "079 123 4567", "079-123-4567", "+962791234567",
                "+962 79 123 4567", "00962791234567")) {
            assertEquals(Optional.of("+962791234567"), PhoneNumbers.international(typed, true), typed);
        }
        assertEquals(Optional.of("+962771234567"), PhoneNumbers.international("0771234567", true));
        assertEquals(Optional.of("+962781234567"), PhoneNumbers.international("0781234567", true));
    }

    @Test
    @DisplayName("numbers that can't exist are refused")
    void invalidNumbers() {
        for (String typed : List.of("0761234567", "079123456", "07912345678", "12ab", "+962", "", "   ")) {
            assertTrue(PhoneNumbers.international(typed, true).isEmpty(), typed);
        }
        assertTrue(PhoneNumbers.international(null, false).isEmpty());
    }

    @Test
    @DisplayName("landlines can't get a text message, but are fine for a branch")
    void landlines() {
        assertTrue(PhoneNumbers.international("06 461 2345", true).isEmpty());
        assertEquals(Optional.of("+96264612345"), PhoneNumbers.international("06 461 2345", false));
    }

    @Test
    @DisplayName("other countries are checked with their own rules")
    void otherCountries() {
        assertEquals(Optional.of("+966501234567"), PhoneNumbers.international("+966 50 123 4567", true), "Saudi Arabia");
        assertTrue(PhoneNumbers.international("+966 50 123 456", true).isEmpty(), "one digit short");
        assertEquals(Optional.of("+447911123456"), PhoneNumbers.international("+44 7911 123456", true), "United Kingdom");
        assertEquals(Optional.of("+14155552671"), PhoneNumbers.international("+1 415 555 2671", true),
                "in the US, mobiles and landlines look the same, so both are accepted");
    }
}
