package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** End-to-end tests of sign-up, bookings, payments and access rules. */
class GymBookingApiTest extends ApiTestBase {

    @Test
    @DisplayName("health check is public")
    void healthIsPublic() throws Exception {
        assertEquals(200, call("GET", "/api/health", null, null).status());
    }

    @Test
    @DisplayName("sign up → code by email → verify → log in")
    void signUpVerifyLogin() throws Exception {
        String email = "signup" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        Reply signUp = call("POST", "/api/auth/signup", null, Map.of(
                "fullName", "New Member", "email", email, "phone", "0790000000", "password", "Secret1234"));
        assertEquals(201, signUp.status(), signUp.body().toString());

        Reply wrongCode = call("POST", "/api/auth/verify", null, Map.of("email", email, "code", "000000"));
        assertEquals(400, wrongCode.status());

        Reply verified = call("POST", "/api/auth/verify", null,
                Map.of("email", email, "code", mailbox.latestVerificationCode(email)));
        assertEquals(200, verified.status(), verified.body().toString());
        assertEquals("MEMBER", verified.body().path("user").path("role").asText());

        Reply login = call("POST", "/api/auth/login", null, Map.of("email", email, "password", "Secret1234"));
        assertEquals(200, login.status());
        assertFalse(login.body().path("token").asText().isBlank());

        Reply wrongPassword = call("POST", "/api/auth/login", null, Map.of("email", email, "password", "Wrong1234"));
        assertEquals(401, wrongPassword.status());
    }

    @Test
    @DisplayName("roles: no token → 401, trainer on member endpoints → 403, member on trainer endpoints → 403")
    void rolesAreEnforced() throws Exception {
        Reply noToken = call("GET", "/api/bookings/mine", null, null);
        assertEquals(401, noToken.status());
        assertEquals("Please log in first", noToken.body().path("message").asText());
        assertEquals(403, call("GET", "/api/bookings/mine", saraToken, null).status());
        assertEquals(403, call("GET", "/api/trainer/requests", newMember(), null).status());
    }

    @Test
    @DisplayName("someone else's booking answers 404: can't see, cancel or pay it")
    void bookingsArePrivate() throws Exception {
        String owner = newMember();
        String stranger = newMember();
        long id = book(owner, sara, "08:00").body().path("id").asLong();

        assertEquals(404, call("GET", "/api/bookings/" + id, stranger, null).status());
        assertEquals(404, call("POST", "/api/bookings/" + id + "/cancel", stranger, null).status());
        assertEquals(404, call("POST", "/api/bookings/" + id + "/payment", stranger, null).status());
        assertEquals(404, call("POST", "/api/trainer/requests/" + id + "/accept", linaToken, null).status(),
                "Lina can't answer a request sent to Sara");
        assertEquals(200, call("GET", "/api/bookings/" + id, owner, null).status());
    }

    @Test
    @DisplayName("only JSON is accepted: an XML body → 415")
    void xmlIsRefused() throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofString("<login><email>a@b.c</email></login>"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(415, response.statusCode());
    }

    @Test
    @DisplayName("two members can't book the same time: the second gets 409 SLOT_NOT_AVAILABLE")
    void noDoubleBooking() throws Exception {
        assertEquals(201, book(newMember(), sara, "14:00").status());
        Reply second = book(newMember(), sara, "14:00");
        assertEquals(409, second.status());
        assertEquals("SLOT_NOT_AVAILABLE", second.code());
    }

    @Test
    @DisplayName("pay flow: request → accept → pay with Stripe → PAID + receipt email → cancel → refund")
    void fullPaymentFlow() throws Exception {
        String member = newMember();
        long id = book(member, sara, "09:00").body().path("id").asLong();

        assertEquals("NOT_ACCEPTED_YET", call("POST", "/api/bookings/" + id + "/payment", member, null).code());

        Reply accepted = call("POST", "/api/trainer/requests/" + id + "/accept", saraToken, Map.of("message", "See you!"));
        assertEquals("ACCEPTED", accepted.body().path("status").asText());
        assertFalse(accepted.body().path("payBy").isNull(), "accepting sets a pay deadline");

        Reply start = call("POST", "/api/bookings/" + id + "/payment", member, null);
        assertEquals(200, start.status(), start.body().toString());
        assertEquals("pk_test_fake", start.body().path("publishableKey").asText());
        assertEquals(0, start.body().path("price").decimalValue().compareTo(new BigDecimal("20")));
        assertEquals(0, start.body().path("amount").decimalValue().compareTo(new BigDecimal("28.21")));
        assertEquals("USD", start.body().path("currency").asText());
        String paymentIntent = paymentIntentOf(start);
        assertEquals("2821", stripe.intent(paymentIntent).get("amount").toString(), "20 JOD = 28.21 USD = 2821 cents");
        assertEquals("usd", stripe.intent(paymentIntent).get("currency"));

        assertEquals("PAYMENT_NOT_COMPLETED", call("POST", "/api/bookings/" + id + "/payment/confirm", member, null).code());

        stripe.pay(paymentIntent, "visa", "4242");
        Reply paid = call("POST", "/api/bookings/" + id + "/payment/confirm", member, null);
        assertEquals(200, paid.status(), paid.body().toString());
        assertEquals("PAID", paid.body().path("status").asText());
        assertEquals("Visa •••• 4242", paid.body().path("payment").path("method").asText());
        assertTrue(paid.body().path("canCancel").asBoolean(), "more than 24 h before: still cancellable");

        String email = memberEmail(member);
        assertEquals(1, mailbox.count(email, "Booking confirmed"), "exactly one receipt");
        assertTrue(mailbox.to(email).stream().anyMatch(e -> e.body().contains("28.21 USD (20.000 JOD) with Visa •••• 4242")));
        assertEquals(1, mailbox.count("sara.trainer@gym.com", "Session confirmed"));

        assertEquals("PAID", call("POST", "/api/bookings/" + id + "/payment/confirm", member, null).body().path("status").asText());
        assertEquals(1, mailbox.count(email, "Booking confirmed"));
        assertEquals("ALREADY_PAID", call("POST", "/api/bookings/" + id + "/payment", member, null).code());

        Reply cancelled = call("POST", "/api/bookings/" + id + "/cancel", member, null);
        assertEquals("CANCELLED", cancelled.body().path("status").asText());
        assertEquals("REFUNDED", cancelled.body().path("payment").path("status").asText());
        assertEquals(1, stripe.refundsFor(paymentIntent).size());
        assertEquals(1, mailbox.count(email, "Refund"));
    }

    @Test
    @DisplayName("a declined card or a slow bank doesn't confirm the booking")
    void declinedAndProcessing() throws Exception {
        String member = newMember();
        long id = acceptedBooking(member, lina, "10:00");
        String paymentIntent = paymentIntentOf(call("POST", "/api/bookings/" + id + "/payment", member, null));

        stripe.decline(paymentIntent);
        assertEquals("PAYMENT_NOT_COMPLETED", call("POST", "/api/bookings/" + id + "/payment/confirm", member, null).code());
        stripe.processing(paymentIntent);
        assertEquals("PAYMENT_PROCESSING", call("POST", "/api/bookings/" + id + "/payment/confirm", member, null).code());
        assertEquals("ACCEPTED", call("GET", "/api/bookings/" + id, member, null).body().path("status").asText());

        // Starting payment again must reuse the existing PaymentIntent
        int before = stripe.paymentIntentCount();
        call("POST", "/api/bookings/" + id + "/payment", member, null);
        assertEquals(before, stripe.paymentIntentCount());
    }

    @Test
    @DisplayName("Stripe's webhook: only correctly signed messages count, and it confirms the booking")
    void webhook() throws Exception {
        String member = newMember();
        long id = acceptedBooking(member, lina, "07:00");
        String paymentIntent = paymentIntentOf(call("POST", "/api/bookings/" + id + "/payment", member, null));
        stripe.pay(paymentIntent, "mastercard", "4444");

        String event = "{\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"" + paymentIntent + "\"}}}";
        long now = System.currentTimeMillis() / 1000;

        assertEquals(400, webhook(event, "t=" + now + ",v1=" + sign("whsec_wrong", now, event)).status());
        assertEquals(400, webhook(event, null).status());
        assertEquals("ACCEPTED", call("GET", "/api/bookings/" + id, member, null).body().path("status").asText());

        assertEquals(200, webhook(event, "t=" + now + ",v1=" + sign(WEBHOOK_SECRET, now, event)).status());
        JsonNode booking = call("GET", "/api/bookings/" + id, member, null).body();
        assertEquals("PAID", booking.path("status").asText());
        assertEquals("Mastercard •••• 4444", booking.path("payment").path("method").asText());

        // Stripe can deliver the same event more than once
        assertEquals(200, webhook(event, "t=" + now + ",v1=" + sign(WEBHOOK_SECRET, now, event)).status());
        assertEquals(1, mailbox.count(memberEmail(member), "Booking confirmed"));
    }

    @Test
    @DisplayName("if Stripe fails to refund, the cancel is undone: still PAID (502)")
    void refundFailureUndoesCancel() throws Exception {
        String member = newMember();
        long id = paidBooking(member, sara, "11:00");

        stripe.failNextRefund();
        Reply cancel = call("POST", "/api/bookings/" + id + "/cancel", member, null);
        assertEquals(502, cancel.status());
        assertEquals("PAYMENT_PROVIDER_ERROR", cancel.code());

        JsonNode booking = call("GET", "/api/bookings/" + id, member, null).body();
        assertEquals("PAID", booking.path("status").asText());
        assertEquals("SUCCEEDED", booking.path("payment").path("status").asText());
    }

    @Test
    @DisplayName("paid after the deadline (payment screen left open) → refunded automatically")
    void paidTooLate() throws Exception {
        String member = newMember();
        long id = acceptedBooking(member, sara, "12:00");
        String paymentIntent = paymentIntentOf(call("POST", "/api/bookings/" + id + "/payment", member, null));

        // Move the pay deadline into the past
        jdbc().update("UPDATE bookings SET pay_by_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now(AMMAN).minusMinutes(1)), id);
        assertEquals("EXPIRED", call("GET", "/api/bookings/" + id, member, null).body().path("status").asText());
        assertEquals("BOOKING_EXPIRED", call("POST", "/api/bookings/" + id + "/payment", member, null).code());

        // The member completes the payment sheet that was already open
        stripe.pay(paymentIntent, "visa", "4242");
        Reply confirm = call("POST", "/api/bookings/" + id + "/payment/confirm", member, null);
        assertEquals("PAID_TOO_LATE", confirm.code());
        assertEquals(1, stripe.refundsFor(paymentIntent).size(), "money given back");
        assertEquals("REFUNDED", call("GET", "/api/bookings/" + id, member, null).body().path("payment").path("status").asText());
    }

    @Test
    @DisplayName("paid, less than 24 h before the start → can't cancel")
    void tooLateToCancel() throws Exception {
        String member = newMember();
        long id = paidBooking(member, sara, "13:00");

        // Move the session to 3 hours from now, inside the 24 h cancellation cutoff
        LocalDateTime soon = LocalDateTime.now(AMMAN).plusHours(3).withSecond(0).withNano(0);
        jdbc().update("UPDATE bookings SET session_date = ?, start_time = ?, end_time = ?, refundable_until = ? WHERE id = ?",
                java.sql.Date.valueOf(soon.toLocalDate()), java.sql.Time.valueOf(soon.toLocalTime()),
                java.sql.Time.valueOf(soon.toLocalTime().plusHours(1)), Timestamp.valueOf(soon.minusHours(24)), id);

        assertFalse(call("GET", "/api/bookings/" + id, member, null).body().path("canCancel").asBoolean());
        Reply cancel = call("POST", "/api/bookings/" + id + "/cancel", member, null);
        assertEquals(409, cancel.status());
        assertEquals("TOO_LATE_TO_CANCEL", cancel.code());
    }

    private static Reply webhook(String payload, String signature) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/payments/stripe/webhook"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload));
        if (signature != null) {
            request.header("Stripe-Signature", signature);
        }
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body()));
    }

    private static String sign(String secret, long timestamp, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }
}
