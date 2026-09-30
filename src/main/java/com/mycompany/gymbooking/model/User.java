package com.mycompany.gymbooking.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;

/** Common account, email-verification and login-lock state for all user types. */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type")
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(length = 6)
    private String verificationCode;

    private LocalDateTime verificationCodeExpiresAt;

    /** Wrong attempts for the current code; reset when a new code is issued. */
    @Column(nullable = false)
    private int verificationAttempts = 0;

    /** Used to enforce the resend cooldown. */
    private LocalDateTime verificationCodeSentAt;

    /** Consecutive failed logins; reset on success or when a lock starts. */
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private LocalDateTime loginLockedUntil;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected User() {
    }

    protected User(String fullName, String email, String phone, String passwordHash) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public abstract Role getRole();

    /** Human-readable title shown in the app. */
    public abstract String getDisplayTitle();

    /** Replaces the current code and resets the attempt counter. */
    public void issueVerificationCode(String code, LocalDateTime sentAt, LocalDateTime expiresAt) {
        this.verificationCode = code;
        this.verificationCodeSentAt = sentAt;
        this.verificationCodeExpiresAt = expiresAt;
        this.verificationAttempts = 0;
    }

    /** Seconds until another code may be sent, rounded up; 0 if allowed now. */
    public long secondsUntilNewCodeAllowed(LocalDateTime now, long cooldownSeconds) {
        if (verificationCodeSentAt == null) {
            return 0;
        }
        long millisLeft = Duration.between(now, verificationCodeSentAt.plusSeconds(cooldownSeconds)).toMillis();
        return millisLeft <= 0 ? 0 : (millisLeft + 999) / 1000;
    }

    /** Records a wrong code and returns the remaining attempts. */
    public int recordWrongCode(int maxAttempts) {
        verificationAttempts++;
        return Math.max(0, maxAttempts - verificationAttempts);
    }

    public boolean hasNoCodeAttemptsLeft(int maxAttempts) {
        return verificationAttempts >= maxAttempts;
    }

    public boolean isVerificationCodeExpired(LocalDateTime now) {
        return verificationCodeExpiresAt == null || now.isAfter(verificationCodeExpiresAt);
    }

    public boolean verificationCodeMatches(String code) {
        return verificationCode != null && verificationCode.equals(code);
    }

    public void markVerified() {
        this.verified = true;
        this.verificationCode = null;
        this.verificationCodeExpiresAt = null;
        this.verificationCodeSentAt = null;
        this.verificationAttempts = 0;
    }

    public boolean isLoginLocked(LocalDateTime now) {
        return loginLockedUntil != null && now.isBefore(loginLockedUntil);
    }

    /**
     * Records a failed login and locks the account until {@code lockUntil} once maxAttempts is reached.
     * @return true if this call locked the account
     */
    public boolean recordFailedLogin(int maxAttempts, LocalDateTime lockUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            loginLockedUntil = lockUntil;
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin() {
        failedLoginAttempts = 0;
        loginLockedUntil = null;
    }

    public LocalDateTime getLoginLockedUntil() {
        return loginLockedUntil;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public boolean isVerified() {
        return verified;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
