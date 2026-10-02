package com.mycompany.gymbooking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * A long-lived token the app trades for a new access token. Only its SHA-256 hash is stored, so a
 * database leak doesn't expose usable tokens. Each one is used once: refreshing revokes it and
 * issues a new one.
 */
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_user", columnList = "user_id"))
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** The user's token version when issued; the token stops working once the user's version moves on. */
    @Column(nullable = false)
    private int userVersion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, LocalDateTime now, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.userVersion = user.getTokenVersion();
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    /** Not revoked, not expired, and issued since the user's sessions were last ended. */
    public boolean isUsableAt(LocalDateTime now) {
        return revokedAt == null && now.isBefore(expiresAt) && userVersion == user.getTokenVersion();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean wasRevokedWithin(Duration period, LocalDateTime now) {
        return revokedAt != null && !now.isAfter(revokedAt.plus(period));
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
