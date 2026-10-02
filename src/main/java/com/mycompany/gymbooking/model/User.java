package com.mycompany.gymbooking.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Common account, email-verification, login-lock and two-factor state for all user types. */
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

    /**
     * Stamped into every access and refresh token. Increasing it ends all of the user's sessions,
     * e.g. after a password change or "log out of all devices".
     */
    @Column(name = "token_version", nullable = false, columnDefinition = "int default 0 not null")
    private int tokenVersion = 0;

    /** Secret shared with the user's authenticator app (Base32); null while two-factor login is off. */
    @Column(length = 32)
    private String twoFactorSecret;

    /** A new secret during setup. It replaces twoFactorSecret once a code from it is confirmed. */
    @Column(length = 32)
    private String pendingTwoFactorSecret;

    /** The 30-second step of the last accepted code, so each code works only once. */
    private Long twoFactorLastStep;

    /** SHA-256 hashes of the unused recovery codes, for logging in without the phone. */
    @ElementCollection
    @CollectionTable(name = "recovery_codes", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "code_hash", nullable = false, length = 64)
    private Set<String> recoveryCodeHashes = new HashSet<>();

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

    /** Inactive accounts can't log in and their tokens are rejected. */
    public boolean isActive() {
        return true;
    }

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

    public LocalDateTime getVerificationCodeExpiresAt() {
        return verificationCodeExpiresAt;
    }

    public boolean isVerificationCodeExpired(LocalDateTime now) {
        return verificationCodeExpiresAt == null || now.isAfter(verificationCodeExpiresAt);
    }

    public boolean hasVerificationCode() {
        return verificationCode != null;
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

    /** Ends existing sessions too, since they were opened under the old email. */
    public void changeEmail(String email) {
        this.email = email;
        endAllSessions();
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

    public int getTokenVersion() {
        return tokenVersion;
    }

    /** Invalidates every access and refresh token issued so far, on all devices. */
    public void endAllSessions() {
        tokenVersion++;
    }

    /** Whether this kind of account must log in with a code from an authenticator app (see Admin). */
    public boolean requiresTwoFactor() {
        return false;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorSecret != null;
    }

    /** True for an account that must use two-factor login but hasn't set it up yet. */
    public boolean needsTwoFactorSetup() {
        return requiresTwoFactor() && !isTwoFactorEnabled();
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public String getPendingTwoFactorSecret() {
        return pendingTwoFactorSecret;
    }

    /** Keeps the new secret aside until the user proves their app has it; the current one still works. */
    public void startTwoFactorSetup(String secret) {
        this.pendingTwoFactorSecret = secret;
    }

    /** Switches to the pending secret and replaces any old recovery codes. */
    public void enableTwoFactor(long confirmedStep, Collection<String> newRecoveryCodeHashes) {
        this.twoFactorSecret = pendingTwoFactorSecret;
        this.pendingTwoFactorSecret = null;
        this.twoFactorLastStep = confirmedStep;
        recoveryCodeHashes.clear();
        recoveryCodeHashes.addAll(newRecoveryCodeHashes);
    }

    public void disableTwoFactor() {
        this.twoFactorSecret = null;
        this.pendingTwoFactorSecret = null;
        this.twoFactorLastStep = null;
        recoveryCodeHashes.clear();
    }

    /** True if a code from this step was already accepted. */
    public boolean isTwoFactorStepUsed(long step) {
        return twoFactorLastStep != null && step <= twoFactorLastStep;
    }

    public void recordTwoFactorStep(long step) {
        this.twoFactorLastStep = step;
    }

    /** Uses up a recovery code; false if it isn't one of the unused codes. */
    public boolean useRecoveryCode(String codeHash) {
        return recoveryCodeHashes.remove(codeHash);
    }

    public boolean isVerified() {
        return verified;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
