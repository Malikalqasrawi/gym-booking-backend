package com.mycompany.gymbooking.notification;

import com.mycompany.gymbooking.model.OutgoingEmail;
import com.mycompany.gymbooking.repository.OutgoingEmailRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves each email in the outgoing_emails table (the "outbox") as part of the caller's transaction,
 * instead of sending it right away. If the transaction rolls back, the email is never sent; once it
 * commits, EmailDispatcher sends the email in the background, so a slow or unreachable mail server
 * doesn't slow down or break the request.
 */
@Component
@ConditionalOnExpression("'${app.notifications.mode:console}' != 'test'")   // tests capture emails instead
public class OutboxNotificationSender implements NotificationSender {

    /** Published when an email is saved; EmailDispatcher sends it once the transaction commits. */
    public record EmailQueued(Long emailId) {
    }

    private final OutgoingEmailRepository emails;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OutboxNotificationSender(OutgoingEmailRepository emails, ApplicationEventPublisher events, Clock clock) {
        this.emails = emails;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional   // joins the caller's transaction, or starts one if there's none
    public void send(String recipient, String subject, String body) {
        OutgoingEmail email = emails.save(new OutgoingEmail(recipient, subject, body, LocalDateTime.now(clock)));
        events.publishEvent(new EmailQueued(email.getId()));
    }
}
