package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * 429: slow down. Used for "wait before asking for a new code" and "account locked for 15 minutes".
 *
 * It also tells the app HOW LONG to wait, in the standard "Retry-After" header (in seconds).
 */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String code, String message, long retryAfterSeconds) {
        super(code, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }

    /** POLYMORPHISM: only this error type adds a header; the others use ApiException's empty version. */
    @Override
    public void writeHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    }

    /** Human-friendly wait: 1 → "1 second", 42 → "42 seconds", 900 → "15 minutes". */
    public static String waitText(long seconds) {
        if (seconds < 120) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = (seconds + 59) / 60;   // round up, so we never say less than the real wait
        return minutes + " minutes";
    }
}
