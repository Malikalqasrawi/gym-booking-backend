package com.mycompany.gymbooking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development version: instead of emailing, it prints the message in the NetBeans Output window.
 * Active when app.notifications.mode=console (also the default if the setting is missing).
 */
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
