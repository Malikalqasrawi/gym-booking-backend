package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpStatus;

/** 401: we don't know who you are (wrong email/password, missing token). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
