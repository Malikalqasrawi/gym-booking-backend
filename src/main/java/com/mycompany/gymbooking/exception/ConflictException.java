package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpStatus;

/** 409: the request clashes with existing data (e.g. email already registered). */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
