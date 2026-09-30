package com.mycompany.gymbooking.notification;

/**
 * INTERFACE = a contract: "anything that can send a message to a user".
 *
 * AuthService only knows about THIS interface, not about Gmail or consoles.
 * That means we can swap how messages are sent without touching AuthService:
 *
 *   ConsoleNotificationSender  → prints to the NetBeans console   (development)
 *   EmailNotificationSender    → sends a real email via SMTP       (production)
 *   (later) SmsNotificationSender → sends a text message
 *
 * Which one is used is decided by  app.notifications.mode  in application.properties.
 */
public interface NotificationSender {

    void send(String recipient, String subject, String body);
}
