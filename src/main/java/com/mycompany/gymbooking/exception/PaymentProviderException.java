package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpStatus;

/** The payment provider rejected the request or could not be reached. */
public class PaymentProviderException extends ApiException {

    public PaymentProviderException(String message) {
        super("PAYMENT_PROVIDER_ERROR", message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_GATEWAY;
    }
}
