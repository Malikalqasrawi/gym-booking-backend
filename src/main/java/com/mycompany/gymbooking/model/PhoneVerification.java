package com.mycompany.gymbooking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The latest SMS code sent to a user, and the limits on it: how soon another may be sent, how many
 * per day, and how many wrong tries are left. The code itself is kept by the SMS provider.
 */
@Entity
@Table(name = "phone_verifications")
public class PhoneVerification {

    /** One row per user, created with their first code. */
    @Id
    private Long userId;

    /** The number the latest code went to; a code is only good for that number. */
    @Column(length = 20)
    private String phone;

    private LocalDateTime sentAt;

    /** Null once the code was used or is no longer valid. */
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int wrongAttempts;

    /** Codes sent on {@link #sendsDay}, to cap what one account can cost in text messages. */
    @Column(nullable = false)
    private int sendsThatDay;

    private LocalDate sendsDay;

    @Version
    private Long version;

    protected PhoneVerification() {
    }

    public PhoneVerification(Long userId) {
        this.userId = userId;
    }

    /**
     * Seconds until another code may be sent, rounded up; 0 if allowed now. This also applies after
     * changing the number, or changing it each time would allow a burst of paid text messages.
     */
    public long secondsUntilResend(LocalDateTime now, long cooldownSeconds) {
        if (sentAt == null) {
            return 0;
        }
        long millisLeft = Duration.between(now, sentAt.plusSeconds(cooldownSeconds)).toMillis();
        return millisLeft <= 0 ? 0 : (millisLeft + 999) / 1000;
    }

    public boolean dailyLimitReached(LocalDate today, int maxPerDay) {
        return today.equals(sendsDay) && sendsThatDay >= maxPerDay;
    }

    public void codeSent(String phone, LocalDateTime now, LocalDateTime expiresAt) {
        if (!now.toLocalDate().equals(sendsDay)) {
            sendsDay = now.toLocalDate();
            sendsThatDay = 0;
        }
        sendsThatDay++;
        this.phone = phone;
        this.sentAt = now;
        this.expiresAt = expiresAt;
        this.wrongAttempts = 0;
    }

    /** Whether a code was sent to this number and can still be entered. */
    public boolean hasUsableCode(String phone, LocalDateTime now) {
        return expiresAt != null && phone.equals(this.phone) && !now.isAfter(expiresAt);
    }

    public boolean hasNoAttemptsLeft(int maxAttempts) {
        return wrongAttempts >= maxAttempts;
    }

    /** Returns how many tries are left. */
    public int recordWrongCode(int maxAttempts) {
        wrongAttempts++;
        return Math.max(0, maxAttempts - wrongAttempts);
    }

    /** The code was used, or the provider no longer accepts it. */
    public void endCode() {
        expiresAt = null;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
