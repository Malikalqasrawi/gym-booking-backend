package com.mycompany.gymbooking.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductionChecksTest {

    @Test
    @DisplayName("production mode refuses codes in the log and the demo trainers; development allows them")
    void productionMode() {
        assertDoesNotThrow(() -> new ProductionChecks(false, "console", "", "", "", true));
        assertDoesNotThrow(() -> new ProductionChecks(true, "email", "ACx", "token", "VAx", false));

        IllegalStateException problems = assertThrows(IllegalStateException.class,
                () -> new ProductionChecks(true, "console", "", "", "", true));
        assertTrue(problems.getMessage().contains("app.notifications.mode must be email"), problems.getMessage());
        assertTrue(problems.getMessage().contains("app.sms.twilio"), problems.getMessage());
        assertTrue(problems.getMessage().contains("app.seed.demo-trainers must be off"), problems.getMessage());
    }
}
