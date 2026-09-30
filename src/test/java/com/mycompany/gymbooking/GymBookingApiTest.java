package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.notification.NotificationSender;
import com.mycompany.gymbooking.support.CapturingNotificationSender;
import com.mycompany.gymbooking.support.FakeStripe;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * API tests: the WHOLE backend starts (Spring, security, JPA, the booking and payment rules),
 * and the tests call it over real HTTP, exactly like the Flutter app does.
 *
 * Replaced for the test only:
 *   - MySQL  → H2, an in-memory database (empty at every run, gone afterwards)
 *   - Stripe → FakeStripe (a local pretend Stripe we control)
 *   - email  → CapturingNotificationSender (keeps the emails in a list)
 *
 * The backend starts once for the whole class. Each test uses its own members and time slots,
 * so the tests don't depend on each other.
 */
class GymBookingApiTest {

    private static final String WEBHOOK_SECRET = "whsec_test_only";
    private static final ZoneId AMMAN = ZoneId.of("Asia/Amman");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final AtomicInteger MEMBER_NUMBER = new AtomicInteger();

    private static FakeStripe stripe;
    private static CapturingNotificationSender mailbox;
    private static ConfigurableApplicationContext backend;
    private static String baseUrl;

    // Looked up once
    private static long sara;
    private static long lina;
    private static String saraToken;
    private static String linaToken;
    /** Next Wednesday: Sara works 08:00–16:00, Lina 07:00–13:00 and 17:00–21:00. */
    private static LocalDate wednesday;

    private record Reply(int status, JsonNode body) {
        String code() {
            return body.path("code").asText();
        }
    }

    // ==================================================================
    // Start / stop the backend
    // ==================================================================

    @BeforeAll
    static void startBackend() throws Exception {
        stripe = FakeStripe.start();
        mailbox = new CapturingNotificationSender();

        // A random JWT secret for this run only (never written anywhere)
        byte[] jwtSecret = new byte[64];
        new SecureRandom().nextBytes(jwtSecret);

        backend = new SpringApplicationBuilder(Gymbooking.class)
                // Our pretend mailbox becomes THE NotificationSender (notifications mode "test" turns off the others)
                .initializers(context -> ((GenericApplicationContext) context)
                        .registerBean(NotificationSender.class, () -> mailbox))
                .run(
                        "--server.port=0",   // any free port
                        "--spring.datasource.url=jdbc:h2:mem:api-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--app.jwt.secret=" + Base64.getEncoder().encodeToString(jwtSecret),
                        "--app.notifications.mode=test",
                        "--app.rate-limit.auth-per-minute=100000",
                        "--app.rate-limit.general-per-minute=100000",
                        "--app.payments.stripe.secret-key=" + FakeStripe.SECRET_KEY,
                        "--app.payments.stripe.publishable-key=pk_test_fake",
                        "--app.payments.stripe.api-base=" + stripe.baseUrl(),
                        "--app.payments.stripe.webhook-secret=" + WEBHOOK_SECRET,
                        "--spring.main.banner-mode=off");
        baseUrl = "http://localhost:" + backend.getEnvironment().getProperty("local.server.port");

        saraToken = login("sara.trainer@gym.com", "Trainer1234");
        linaToken = login("lina.trainer@gym.com", "Trainer1234");
        String member = newMember();
        sara = trainerId(member, "Abdoun Branch", "Sara Haddad");
        lina = trainerId(member, "Sweifieh Branch", "Lina Nasser");

        LocalDate day = LocalDate.now(AMMAN).plusDays(1);
        while (day.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            day = day.plusDays(1);
        }
        wednesday = day;
    }

    @AfterAll
    static void stopBackend() {
        if (backend != null) {
            backend.close();
        }
        if (stripe != null) {
            stripe.close();
        }
    }

    // ==================================================================
    // Accounts and security
    // ==================================================================

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
        assertEquals(401, call("GET", "/api/bookings/mine", null, null).status());
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

    // ==================================================================
    // Booking rules
    // ==================================================================

    @Test
    @DisplayName("two members can't book the same time: the second gets 409 SLOT_NOT_AVAILABLE")
    void noDoubleBooking() throws Exception {
        assertEquals(201, book(newMember(), sara, "14:00").status());
        Reply second = book(newMember(), sara, "14:00");
        assertEquals(409, second.status());
        assertEquals("SLOT_NOT_AVAILABLE", second.code());
    }

    // ==================================================================
    // Payments
    // ==================================================================

    @Test
    @DisplayName("pay flow: request → accept → pay with Stripe → PAID + receipt email → cancel → refund")
    void fullPaymentFlow() throws Exception {
        String member = newMember();
        long id = book(member, sara, "09:00").body().path("id").asLong();

        assertEquals("NOT_ACCEPTED_YET", call("POST", "/api/bookings/" + id + "/payment", member, null).code());

        Reply accepted = call("POST", "/api/trainer/requests/" + id + "/accept", saraToken, Map.of("message", "See you!"));
        assertEquals("ACCEPTED", accepted.body().path("status").asText());
        assertFalse(accepted.body().path("payBy").isNull(), "accepting sets a pay deadline");

        // 1. Start: the backend creates a Stripe payment for 20 JOD
        Reply start = call("POST", "/api/bookings/" + id + "/payment", member, null);
        assertEquals(200, start.status(), start.body().toString());
        assertEquals("pk_test_fake", start.body().path("publishableKey").asText());
        assertEquals(0, start.body().path("amount").decimalValue().compareTo(new BigDecimal("20")));
        String paymentIntent = paymentIntentOf(start);
        assertEquals("20000", stripe.intent(paymentIntent).get("amount").toString(), "20 JOD = 20000 fils");

        // 2. Asking to confirm before paying → nothing happens
        assertEquals("PAYMENT_NOT_COMPLETED", call("POST", "/api/bookings/" + id + "/payment/confirm", member, null).code());

        // 3. The member pays in Stripe's screen, then the app confirms
        stripe.pay(paymentIntent, "visa", "4242");
        Reply paid = call("POST", "/api/bookings/" + id + "/payment/confirm", member, null);
        assertEquals(200, paid.status(), paid.body().toString());
        assertEquals("PAID", paid.body().path("status").asText());
        assertEquals("Visa •••• 4242", paid.body().path("payment").path("method").asText());
        assertTrue(paid.body().path("canCancel").asBoolean(), "more than 24 h before: still cancellable");

        String email = memberEmail(member);
        assertEquals(1, mailbox.count(email, "Booking confirmed"), "exactly one receipt");
        assertEquals(1, mailbox.count("sara.trainer@gym.com", "Session confirmed"));

        // Confirming again changes nothing (and sends no second email)
        assertEquals("PAID", call("POST", "/api/bookings/" + id + "/payment/confirm", member, null).body().path("status").asText());
        assertEquals(1, mailbox.count(email, "Booking confirmed"));
        assertEquals("ALREADY_PAID", call("POST", "/api/bookings/" + id + "/payment", member, null).code());

        // 4. Cancel → full refund at Stripe
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

        // Pressing Pay again reuses the same Stripe payment (no second charge possible)
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

        // Stripe may send the same message twice: still one receipt
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

        // The 12 hours pass...
        jdbc().update("UPDATE bookings SET pay_by_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now(AMMAN).minusMinutes(1)), id);
        assertEquals("EXPIRED", call("GET", "/api/bookings/" + id, member, null).body().path("status").asText());
        assertEquals("BOOKING_EXPIRED", call("POST", "/api/bookings/" + id + "/payment", member, null).code());

        // ...but the member still pays in the screen that was open
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

        // Move the session to 3 hours from now (as if the days had passed)
        LocalDateTime soon = LocalDateTime.now(AMMAN).plusHours(3).withSecond(0).withNano(0);
        jdbc().update("UPDATE bookings SET session_date = ?, start_time = ?, end_time = ?, refundable_until = ? WHERE id = ?",
                java.sql.Date.valueOf(soon.toLocalDate()), java.sql.Time.valueOf(soon.toLocalTime()),
                java.sql.Time.valueOf(soon.toLocalTime().plusHours(1)), Timestamp.valueOf(soon.minusHours(24)), id);

        assertFalse(call("GET", "/api/bookings/" + id, member, null).body().path("canCancel").asBoolean());
        Reply cancel = call("POST", "/api/bookings/" + id + "/cancel", member, null);
        assertEquals(409, cancel.status());
        assertEquals("TOO_LATE_TO_CANCEL", cancel.code());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Reply call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Accept", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null || method.equals("POST")) {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body == null ? Map.of() : body)));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        String text = response.body();
        return new Reply(response.statusCode(), text == null || text.isBlank() ? JSON.createObjectNode() : JSON.readTree(text));
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

    private static String login(String email, String password) throws Exception {
        Reply reply = call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
        assertEquals(200, reply.status(), "login " + email + ": " + reply.body());
        return reply.body().path("token").asText();
    }

    /** Signs up and verifies a brand-new member; returns their login token. */
    private static String newMember() throws Exception {
        String email = "member" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        Reply signUp = call("POST", "/api/auth/signup", null, Map.of(
                "fullName", "Test Member", "email", email, "phone", "0790000000", "password", "Secret1234"));
        assertEquals(201, signUp.status(), signUp.body().toString());
        Reply verified = call("POST", "/api/auth/verify", null,
                Map.of("email", email, "code", mailbox.latestVerificationCode(email)));
        assertEquals(200, verified.status(), verified.body().toString());
        return verified.body().path("token").asText();
    }

    private static String memberEmail(String token) throws Exception {
        return call("GET", "/api/users/me", token, null).body().path("email").asText();
    }

    private static long trainerId(String token, String branchName, String trainerName) throws Exception {
        for (JsonNode branch : call("GET", "/api/branches", token, null).body()) {
            if (branch.path("name").asText().equals(branchName)) {
                for (JsonNode trainer : call("GET", "/api/branches/" + branch.path("id").asLong() + "/trainers", token, null).body()) {
                    if (trainer.path("fullName").asText().equals(trainerName)) {
                        return trainer.path("id").asLong();
                    }
                }
            }
        }
        throw new AssertionError(trainerName + " not found at " + branchName);
    }

    private static Reply book(String member, long trainer, String startTime) throws Exception {
        return call("POST", "/api/bookings", member, Map.of(
                "trainerId", trainer, "date", wednesday.toString(), "startTime", startTime, "durationMinutes", 60));
    }

    private static long acceptedBooking(String member, long trainer, String startTime) throws Exception {
        Reply booked = book(member, trainer, startTime);
        assertEquals(201, booked.status(), booked.body().toString());
        long id = booked.body().path("id").asLong();
        String trainerToken = trainer == sara ? saraToken : linaToken;
        assertEquals(200, call("POST", "/api/trainer/requests/" + id + "/accept", trainerToken, null).status());
        return id;
    }

    private static long paidBooking(String member, long trainer, String startTime) throws Exception {
        long id = acceptedBooking(member, trainer, startTime);
        String paymentIntent = paymentIntentOf(call("POST", "/api/bookings/" + id + "/payment", member, null));
        stripe.pay(paymentIntent, "visa", "4242");
        Reply paid = call("POST", "/api/bookings/" + id + "/payment/confirm", member, null);
        assertEquals("PAID", paid.body().path("status").asText(), paid.body().toString());
        return id;
    }

    /** "pi_test3_secret_test" → "pi_test3" */
    private static String paymentIntentOf(Reply start) {
        String clientSecret = start.body().path("clientSecret").asText();
        assertNotNull(clientSecret);
        return clientSecret.substring(0, clientSecret.indexOf("_secret_"));
    }

    private static JdbcTemplate jdbc() {
        return backend.getBean(JdbcTemplate.class);
    }
}
