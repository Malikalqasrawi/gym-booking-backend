package com.mycompany.gymbooking.notification;

/**
 * Hands one email to the outside world. The implementation is selected by app.notifications.mode:
 * SMTP for "email", the log for "console". A failure throws, and the email is tried again later.
 */
public interface MailTransport {

    /** {@code text} and {@code html} are the same message; mail apps show the one they support. */
    void send(String recipient, String subject, String text, String html);
}
