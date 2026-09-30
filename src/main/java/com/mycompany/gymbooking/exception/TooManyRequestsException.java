package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

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

    @Override
    public void writeHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    }

    /** Formats a wait as seconds below two minutes, otherwise as minutes rounded up so it is never understated. */
    public static String waitText(long seconds) {
        if (seconds < 120) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = (seconds + 59) / 60;
        return minutes + " minutes";
    }
}
