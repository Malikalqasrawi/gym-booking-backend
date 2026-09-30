package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpStatus;

/** 503: this feature is switched off right now (e.g. payments without Stripe keys). */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
