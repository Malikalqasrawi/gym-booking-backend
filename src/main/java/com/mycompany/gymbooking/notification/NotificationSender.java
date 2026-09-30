package com.mycompany.gymbooking.notification;

/** Delivers a message to a user. The implementation is selected by app.notifications.mode. */
public interface NotificationSender {

    void send(String recipient, String subject, String body);
}
