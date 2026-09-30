package com.mycompany.gymbooking.exception;

import org.springframework.http.HttpStatus;

/**
 * 502 Bad Gateway: WE are fine, but the payment provider (Stripe) refused or didn't answer.
 * Example: wrong Stripe key, Stripe unreachable, an amount Stripe doesn't accept.
 */
public class PaymentProviderException extends ApiException {

    public PaymentProviderException(String message) {
        super("PAYMENT_PROVIDER_ERROR", message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_GATEWAY;
    }
}
