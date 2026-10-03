package com.mycompany.gymbooking.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * With app.production=true, refuses to start with settings that are fine on a laptop but unsafe on a
 * real server: emails and SMS codes written to the log instead of being sent, or the demo trainers
 * whose password is in the README.
 */
@Component
public class ProductionChecks {

    public ProductionChecks(@Value("${app.production:false}") boolean production,
                            @Value("${app.notifications.mode:console}") String notificationsMode,
                            @Value("${app.sms.twilio.account-sid:}") String twilioAccountSid,
                            @Value("${app.sms.twilio.auth-token:}") String twilioAuthToken,
                            @Value("${app.sms.twilio.verify-service-sid:}") String twilioVerifyServiceSid,
                            @Value("${app.seed.demo-trainers:false}") boolean demoTrainers) {
        if (!production) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (!notificationsMode.equals("email")) {
            problems.add("app.notifications.mode must be email (EMAIL_MODE=email), or codes are only written to the log");
        }
        if (twilioAccountSid.isBlank() || twilioAuthToken.isBlank() || twilioVerifyServiceSid.isBlank()) {
            problems.add("the three app.sms.twilio.* keys must be set, or SMS codes are only written to the log");
        }
        if (demoTrainers) {
            problems.add("app.seed.demo-trainers must be off: their password is public");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Production mode is on, but " + String.join("; ", problems) + ".");
        }
    }
}
