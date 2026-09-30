package com.mycompany.gymbooking.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** Body of every API error. {@code fieldErrors} is only populated for validation errors. */
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
