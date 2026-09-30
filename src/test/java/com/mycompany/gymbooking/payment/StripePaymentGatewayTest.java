package com.mycompany.gymbooking.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.exception.PaymentProviderException;
import com.mycompany.gymbooking.exception.ServiceUnavailableException;
import com.mycompany.gymbooking.support.FakeStripe;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * StripePaymentGateway against FakeStripe: checks the exact HTTP requests we send to Stripe
 * and how we read Stripe's answers, including errors.
 */
class StripePaymentGatewayTest {

    private static FakeStripe stripe;

    @BeforeAll
    static void startFakeStripe() throws Exception {
        stripe = FakeStripe.start();
    }

    @AfterAll
    static void stopFakeStripe() {
        stripe.close();
    }

    private static StripePaymentGateway gateway(String secretKey, String publishableKey, String apiBase) {
        return new StripePaymentGateway(RestClient.builder(), new ObjectMapper(), secretKey, publishableKey, apiBase);
    }

    private static StripePaymentGateway gateway() {
        return gateway(FakeStripe.SECRET_KEY, "pk_test_fake", stripe.baseUrl());
    }

    private static PaymentOrder order(String key, String amount) {
        return new PaymentOrder(key, new BigDecimal(amount), "JOD", "Session #58", Map.of("booking_id", "58"));
    }

    @Test
    @DisplayName("create: sends fils, lowercase currency, card only, metadata, idempotency key, form encoding")
    void createSendsWhatStripeExpects() {
        GatewayPayment payment = gateway().createPayment(order("gym-payment-create-1", "20.000"));

        FakeStripe.Request sent = stripe.requests("POST", "/v1/payment_intents").stream()
                .filter(r -> "gym-payment-create-1".equals(r.idempotencyKey())).findFirst().orElseThrow();
        assertEquals("20000", sent.form().get("amount"));
        assertEquals("jod", sent.form().get("currency"));
        assertEquals("card", sent.form().get("payment_method_types[]"));
        assertEquals("58", sent.form().get("metadata[booking_id]"));
        assertTrue(sent.contentType().startsWith("application/x-www-form-urlencoded"), sent.contentType());

        assertTrue(payment.id().startsWith("pi_"));
        assertTrue(payment.clientSecret().contains("_secret_"));
        assertEquals(GatewayPaymentStatus.WAITING_FOR_CUSTOMER, payment.status());
        assertEquals(new BigDecimal("20.000"), payment.amount());
        assertEquals("JOD", payment.currency());
    }

    @Test
    @DisplayName("the same idempotency key twice creates ONE payment (a retry can't charge twice)")
    void idempotentCreate() {
        int before = stripe.paymentIntentCount();
        GatewayPayment first = gateway().createPayment(order("gym-payment-retry-1", "15.000"));
        GatewayPayment again = gateway().createPayment(order("gym-payment-retry-1", "15.000"));
        assertEquals(first.id(), again.id());
        assertEquals(before + 1, stripe.paymentIntentCount());
    }

    @Test
    @DisplayName("check: reads status, amount and the card from Stripe's answer")
    void readsPaidPayment() {
        GatewayPayment created = gateway().createPayment(order("gym-payment-read-1", "33.000"));
        assertNull(gateway().getPayment(created.id()).cardLast4(), "no card before paying");

        stripe.processing(created.id());
        assertEquals(GatewayPaymentStatus.PROCESSING, gateway().getPayment(created.id()).status());

        stripe.pay(created.id(), "visa", "4242");
        GatewayPayment paid = gateway().getPayment(created.id());
        assertEquals(GatewayPaymentStatus.SUCCEEDED, paid.status());
        assertEquals("visa", paid.cardBrand());
        assertEquals("4242", paid.cardLast4());
        assertEquals(0, paid.amount().compareTo(new BigDecimal("33")));
    }

    @Test
    @DisplayName("refund: gives the money back once, even if asked twice with the same key")
    void refund() {
        GatewayPayment created = gateway().createPayment(order("gym-payment-refund-1", "20.000"));
        stripe.pay(created.id(), "visa", "4242");

        GatewayRefund refund = gateway().refund(created.id(), "gym-refund-1");
        GatewayRefund again = gateway().refund(created.id(), "gym-refund-1");
        assertEquals(refund.id(), again.id());
        assertEquals("succeeded", refund.status());
        assertEquals(1, stripe.refundsFor(created.id()).size());
        assertEquals("requested_by_customer", stripe.refundsFor(created.id()).get(0).get("reason"));
    }

    @Test
    @DisplayName("Stripe errors become a 502 PaymentProviderException with Stripe's explanation")
    void stripeErrors() {
        PaymentProviderException wrongKey = assertThrows(PaymentProviderException.class,
                () -> gateway("sk_test_wrong", "pk_test_fake", stripe.baseUrl()).createPayment(order("k-wrong", "20.000")));
        assertTrue(wrongKey.getMessage().contains("Invalid API Key"), wrongKey.getMessage());
        assertEquals(502, wrongKey.getStatus().value());

        GatewayPayment created = gateway().createPayment(order("gym-payment-err-1", "20.000"));
        stripe.pay(created.id(), "visa", "4242");
        stripe.failNextRefund();
        assertThrows(PaymentProviderException.class, () -> gateway().refund(created.id(), "gym-refund-err-1"));
    }

    @Test
    @DisplayName("Stripe unreachable → a clear message, not a stack trace")
    void stripeUnreachable() {
        PaymentProviderException error = assertThrows(PaymentProviderException.class,
                () -> gateway(FakeStripe.SECRET_KEY, "pk_test_fake", "http://127.0.0.1:1").createPayment(order("k-down", "20.000")));
        assertTrue(error.getMessage().contains("Couldn't reach Stripe"), error.getMessage());
    }

    @Test
    @DisplayName("an amount Stripe can't take (13.125 JOD) is refused before calling Stripe")
    void badAmountNeverSent() {
        int before = stripe.paymentIntentCount();
        assertThrows(IllegalArgumentException.class, () -> gateway().createPayment(order("k-bad", "13.125")));
        assertEquals(before, stripe.paymentIntentCount());
    }

    @Test
    @DisplayName("no keys → payments switched off (503); live keys → the app refuses to start")
    void keyRules() {
        ServiceUnavailableException off = assertThrows(ServiceUnavailableException.class,
                () -> gateway("", "", stripe.baseUrl()).createPayment(order("k-off", "20.000")));
        assertEquals("PAYMENTS_NOT_CONFIGURED", off.getCode());

        assertThrows(IllegalStateException.class, () -> gateway("sk_live_abc", "pk_live_abc", stripe.baseUrl()));
    }
}
