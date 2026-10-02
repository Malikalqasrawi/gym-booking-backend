package com.mycompany.gymbooking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An email waiting to be sent, or the record of one that was sent or given up on. It's saved in
 * the same transaction as the change it reports, so an email goes out only if that change was
 * saved, and it survives a mail server outage or a restart until it's delivered.
 */
@Entity
@Table(name = "outgoing_emails", indexes = @Index(name = "idx_outgoing_emails_due", columnList = "status, next_attempt_at"))
public class OutgoingEmail {

    public enum Status {
        PENDING,
        SENT,
        /** Every attempt failed; the email won't be tried again. */
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(nullable = false, length = 300)
    private String subject;

    /** Cleared once the email is sent or given up on, so codes and booking details aren't kept. */
    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** When the next attempt may start. Null once the email is sent or failed. */
    private LocalDateTime nextAttemptAt;

    private LocalDateTime sentAt;

    @Column(length = 500)
    private String lastError;

    /** Two senders can't both claim the same attempt: the second one's update fails. */
    @Version
    private Long version;

    protected OutgoingEmail() {
    }

    public OutgoingEmail(String recipient, String subject, String body, LocalDateTime now) {
        this.recipient = recipient;
        this.subject = subject.length() > 300 ? subject.substring(0, 300) : subject;
        this.body = body;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    public boolean isDue(LocalDateTime now) {
        return status == Status.PENDING && !nextAttemptAt.isAfter(now);
    }

    /**
     * Claims the next attempt. Until {@code retryIfUnfinishedAt} nobody else tries, and if the
     * sender stops without reporting back (e.g. the server restarts), the email is tried again then.
     */
    public void startAttempt(LocalDateTime retryIfUnfinishedAt) {
        attempts++;
        nextAttemptAt = retryIfUnfinishedAt;
    }

    public void markSent(LocalDateTime now) {
        status = Status.SENT;
        sentAt = now;
        nextAttemptAt = null;
        lastError = null;
        body = null;
    }

    /** Records a failed attempt: tried again at {@code retryAt}, or given up on when that's null. */
    public void markAttemptFailed(String error, LocalDateTime retryAt) {
        lastError = error == null ? "Unknown error" : error.length() > 500 ? error.substring(0, 500) : error;
        nextAttemptAt = retryAt;
        if (retryAt == null) {
            status = Status.FAILED;
            body = null;
        }
    }

    public Long getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public String getLastError() {
        return lastError;
    }
}
