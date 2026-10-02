package com.mycompany.gymbooking.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Provides a Clock in the gym's timezone so "today" doesn't depend on the server's zone.
 * Services use it instead of now() so tests can supply a fixed clock.
 */
@Configuration
@EnableScheduling   // for the background jobs: expiry, reminders, email retries, cleanup
public class TimeConfig {

    @Bean
    public Clock gymClock(@Value("${app.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
