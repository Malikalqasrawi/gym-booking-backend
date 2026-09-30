package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Base class for every "expected" error in our API (wrong code, email taken, not found...).
 *
 * ABSTRACTION + INHERITANCE: each subclass fixes its own HTTP status,
 * so services just write:   throw new ConflictException("EMAIL_TAKEN", "...");
 * and GlobalExceptionHandler turns ANY ApiException into a proper JSON error.
 */
public abstract class ApiException extends RuntimeException {

    private final String code;

    protected ApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Each subclass decides which HTTP status it represents (polymorphism). */
    public abstract HttpStatus getStatus();

    /** A short machine-readable code the app can check, e.g. "EMAIL_NOT_VERIFIED". */
    public String getCode() {
        return code;
    }

    /** Extra HTTP headers for this error. Most errors have none; subclasses can add some (e.g. Retry-After). */
    public void writeHeaders(HttpHeaders headers) {
        // nothing by default
    }
}
