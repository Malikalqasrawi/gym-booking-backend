package com.mycompany.gymbooking.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Every error the API returns has this same shape, so the app can handle all errors one way:
 * {
 *   "status": 409,
 *   "code": "EMAIL_TAKEN",
 *   "message": "An account with this email already exists",
 *   "fieldErrors": { "email": "Email is not valid" },   (only for validation errors)
 *   "timestamp": "2026-09-28T10:15:30"
 * }
 */
public record ErrorResponse(
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors,
        LocalDateTime timestamp
) {

    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(status, code, message, Map.of(), LocalDateTime.now());
    }

    public static ErrorResponse of(int status, String code, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, code, message, fieldErrors, LocalDateTime.now());
    }
}
