package com.mycompany.gymbooking.dto;

/**
 * Returned after a successful login or verification:
 * {
 *   "token": "eyJhbGciOi...",
 *   "user":  { "id": 1, "fullName": "...", "role": "MEMBER", ... }
 * }
 * The app saves the token and sends it with every later request.
 */
public record AuthResponse(String token, UserResponse user) {
}
