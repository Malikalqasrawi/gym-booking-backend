package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpStatus;

/** 400: the request makes no sense (e.g. wrong verification code). */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
