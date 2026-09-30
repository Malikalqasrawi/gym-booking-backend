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

/**
 * ABSTRACTION + INHERITANCE
 *
 * "User" is abstract: nobody is just a "User", they are always a Member, Trainer or Admin.
 * It holds everything the three have in common (name, email, password, verification...).
 *
 * Database: @Entity makes Hibernate create a table "users" for this class.
 * SINGLE_TABLE means Member, Trainer and Admin rows all live in that one table,
 * and the column "user_type" says which class each row is.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type")
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // database gives 1, 2, 3...
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    // ENCAPSULATION: we never store the real password, only its hash (BCrypt).
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(length = 6)
    private String verificationCode;

    private LocalDateTime verificationCodeExpiresAt;

    /** How many wrong codes were typed for the current code (reset when a new code is sent). */
    @Column(nullable = false)
    private int verificationAttempts = 0;

    /** When the last code was sent (used for the "wait 60 seconds before a new code" rule). */
    private LocalDateTime verificationCodeSentAt;

    /** Wrong passwords in a row (reset after a correct login or when a lock starts). */
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    /** While this time is in the future, login is refused (null = not locked). */
    private LocalDateTime loginLockedUntil;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA needs an empty constructor. "protected" so only JPA and subclasses use it. */
    protected User() {
    }

    protected User(String fullName, String email, String phone, String passwordHash) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    /** Runs automatically right before the row is first saved. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ----------------------------------------------------------------
    // POLYMORPHISM: every subclass MUST give its own answer to these.
    // ----------------------------------------------------------------

    /** Which role this user has (used for permissions). */
    public abstract Role getRole();

    /** A friendly title shown in the app, e.g. "Trainer · Strength coach". */
    public abstract String getDisplayTitle();

    // ----------------------------------------------------------------
    // Behaviour (methods that protect the object's rules)
    // ----------------------------------------------------------------

    /** Stores a new code, sent at `sentAt` and valid until `expiresAt`. */
    public void issueVerificationCode(String code, LocalDateTime sentAt, LocalDateTime expiresAt) {
        this.verificationCode = code;
        this.verificationCodeSentAt = sentAt;
        this.verificationCodeExpiresAt = expiresAt;
        this.verificationAttempts = 0;   // a new code gets a fresh set of tries
    }

    /** Seconds left before another code may be sent (0 = allowed now). */
    public long secondsUntilNewCodeAllowed(LocalDateTime now, long cooldownSeconds) {
        if (verificationCodeSentAt == null) {
            return 0;
        }
        long millisLeft = Duration.between(now, verificationCodeSentAt.plusSeconds(cooldownSeconds)).toMillis();
        return millisLeft <= 0 ? 0 : (millisLeft + 999) / 1000;   // round up: 0.3 s left → "1 second"
    }

    /** Counts one wrong code and returns how many tries are left (never below 0). */
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

    /** Marks the email as verified and throws away the used code. */
    public void markVerified() {
        this.verified = true;
        this.verificationCode = null;
        this.verificationCodeExpiresAt = null;
        this.verificationCodeSentAt = null;
        this.verificationAttempts = 0;
    }

    // ----------------------------------------------------------------
    // Login lock (too many wrong passwords)
    // ----------------------------------------------------------------

    public boolean isLoginLocked(LocalDateTime now) {
        return loginLockedUntil != null && now.isBefore(loginLockedUntil);
    }

    /**
     * Counts one wrong password. On the last allowed try, the account is locked until `lockUntil`.
     * @return true if this wrong password just locked the account
     */
    public boolean recordFailedLogin(int maxAttempts, LocalDateTime lockUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            loginLockedUntil = lockUntil;
            failedLoginAttempts = 0;   // after the lock ends, a fresh set of tries
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

    // ----------------------------------------------------------------
    // Getters and setters (only where changing the value is allowed)
    // ----------------------------------------------------------------

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
