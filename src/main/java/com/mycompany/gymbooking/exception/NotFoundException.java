package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpStatus;

/** 404: the thing you asked for doesn't exist. */
public class NotFoundException extends ApiException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
