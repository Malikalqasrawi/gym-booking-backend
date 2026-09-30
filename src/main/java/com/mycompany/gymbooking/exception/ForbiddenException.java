package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpStatus;

/** 403: we know who you are, but you're not allowed (e.g. email not verified yet). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
