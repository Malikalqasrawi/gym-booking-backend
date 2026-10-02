package com.mycompany.gymbooking.dto;

/**
 * A new session: {@code token} is the short-lived access token sent with every request, and
 * {@code refreshToken} is traded at /api/auth/refresh for a new pair when it expires.
 */
public record AuthResponse(String token, String refreshToken, UserResponse user) {
}
