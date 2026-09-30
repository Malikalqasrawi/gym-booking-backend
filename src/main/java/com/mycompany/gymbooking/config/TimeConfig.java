package com.mycompany.gymbooking.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The gym's clock.
 *
 * Instead of calling LocalDate.now() directly, services ask for this Clock.
 * Two benefits:
 *   1. "Today" and "now" are in Amman time, even if the server runs in another country.
 *   2. In a unit test you can pass a fixed clock (e.g. always 2026-10-04 09:00) and get predictable results.
 */
@Configuration
@EnableScheduling   // turns on @Scheduled methods (BookingExpiryJob)
public class TimeConfig {

    @Bean
    public Clock gymClock(@Value("${app.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
