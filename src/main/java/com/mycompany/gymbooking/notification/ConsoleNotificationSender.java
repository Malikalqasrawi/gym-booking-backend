package com.mycompany.gymbooking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development sender that logs messages instead of emailing them. Used by default. */
@Component
@ConditionalOnProperty(name = "app.notifications.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotificationSender.class);

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("""

                ==================== EMAIL (console mode) ====================
                To:      {}
                Subject: {}

                {}
                ==============================================================
                """, recipient, subject, body);
    }
}
