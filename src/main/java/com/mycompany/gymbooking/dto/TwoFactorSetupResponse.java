package com.mycompany.gymbooking.dto;

/**
 * A new secret for the authenticator app: {@code otpauthUri} goes into a QR code to scan, and
 * {@code secret} can be typed in by hand instead.
 */
public record TwoFactorSetupResponse(String secret, String otpauthUri) {
}
