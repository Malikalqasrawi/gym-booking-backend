package com.mycompany.gymbooking.notification;

/**
 * Delivers a message to a user. In the app this is OutboxNotificationSender, which saves the email
 * with the caller's transaction and sends it once that commits; tests replace it to read the emails.
 */
public interface NotificationSender {

    void send(String recipient, String subject, String body);
}
