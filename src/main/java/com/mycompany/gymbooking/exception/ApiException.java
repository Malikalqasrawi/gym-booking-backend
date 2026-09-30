package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Base class for expected API errors. Each subclass defines its HTTP status, and
 * GlobalExceptionHandler renders it as an ErrorResponse.
 */
public abstract class ApiException extends RuntimeException {

    private final String code;

    protected ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public abstract HttpStatus getStatus();

    /** Machine-readable error code for clients. */
    public String getCode() {
        return code;
    }

    /** Adds error-specific response headers. No-op by default. */
    public void writeHeaders(HttpHeaders headers) {
    }
}
