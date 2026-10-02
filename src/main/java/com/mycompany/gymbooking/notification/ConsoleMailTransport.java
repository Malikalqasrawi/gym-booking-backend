package com.mycompany.gymbooking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development transport that logs the text version of each email instead of sending it. Used by default. */
@Component
@ConditionalOnProperty(name = "app.notifications.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(ConsoleMailTransport.class);

    @Override
    public void send(String recipient, String subject, String text, String html) {
        log.info("""

                ==================== EMAIL (console mode) ====================
                To:      {}
                Subject: {}

                {}
                ==============================================================
                """, recipient, subject, text);
    }
}
