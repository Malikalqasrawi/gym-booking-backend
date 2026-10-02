package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.notification.NotificationSender;
import com.mycompany.gymbooking.phone.PhoneCodes;
import com.mycompany.gymbooking.security.Totp;
import com.mycompany.gymbooking.support.CapturingNotificationSender;
import com.mycompany.gymbooking.support.FakeGoogle;
import com.mycompany.gymbooking.support.FakeSms;
import com.mycompany.gymbooking.support.FakeStripe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the full application once per test class, over HTTP, with an in-memory H2 database (one per
 * class), FakeStripe instead of Stripe, FakeGoogle instead of Google's sign-in keys,
 * CapturingNotificationSender instead of email and FakeSms instead of text messages. Tests use
 * their own members and time slots so they stay independent within a class.
 */
abstract class ApiTestBase {

    protected static final String WEBHOOK_SECRET = "whsec_test_only";
    protected static final String ADMIN_EMAIL = "admin@gym.com";
    /** The first admin's password, set through configuration like in local.properties. */
    protected static final String ADMIN_PASSWORD = "AdminTest123";
    protected static final ZoneId AMMAN = ZoneId.of("Asia/Amman");
    protected static final ObjectMapper JSON = new ObjectMapper();
    protected static final HttpClient HTTP = HttpClient.newHttpClient();
    protected static final AtomicInteger MEMBER_NUMBER = new AtomicInteger();

    /** The admin's authenticator-app secret, set up by the first adminLogin() of each test class. */
    protected static String adminSecret;
    protected static FakeStripe stripe;
    protected static FakeGoogle google;
    protected static CapturingNotificationSender mailbox;
    protected static FakeSms sms;
    protected static ConfigurableApplicationContext backend;
    protected static String baseUrl;

    protected static long sara;
    protected static long lina;
    protected static String saraToken;
    protected static String linaToken;
    /** Next Wednesday. Seeded schedules: Sara 08:00-16:00, Lina 07:00-13:00 and 17:00-21:00. */
    protected static LocalDate wednesday;

    protected record Reply(int status, JsonNode body) {
        String code() {
            return body.path("code").asText();
        }
    }

    @BeforeAll
    static void startBackend() throws Exception {
        stripe = FakeStripe.start();
        google = FakeGoogle.start();
        mailbox = new CapturingNotificationSender();
        sms = new FakeSms();

        byte[] jwtSecret = new byte[64];
        new SecureRandom().nextBytes(jwtSecret);

        backend = new SpringApplicationBuilder(Gymbooking.class)
                // Notifications mode "test" disables the real email and SMS senders, leaving only these
                .initializers(context -> {
                    ((GenericApplicationContext) context).registerBean(NotificationSender.class, () -> mailbox);
                    ((GenericApplicationContext) context).registerBean(PhoneCodes.class, () -> sms);
                })
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
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
                        "--app.admin.initial-password=" + ADMIN_PASSWORD,
                        "--app.auth.google.client-id=" + FakeGoogle.CLIENT_ID,
                        "--app.auth.google.jwks-uri=" + google.keysUrl(),
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
        if (google != null) {
            google.close();
        }
    }

    protected static Reply call(String method, String path, String token, Object body) throws Exception {
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

    protected static String login(String email, String password) throws Exception {
        Reply reply = call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
        assertEquals(200, reply.status(), "login " + email + ": " + reply.body());
        return reply.body().path("token").asText();
    }

    /**
     * Logs in as the admin with a code from the authenticator app, like the app does. The first
     * login of each class (each has a new database) sets the app up.
     */
    protected static String adminLogin() throws Exception {
        Reply login = call("POST", "/api/auth/login", null, Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        assertEquals(200, login.status(), login.body().toString());
        String challenge = login.body().path("challengeToken").asText();
        if (login.body().path("twoFactor").asText().equals("SETUP_REQUIRED")) {
            adminSecret = call("POST", "/api/auth/login/2fa/setup", null, Map.of("challengeToken", challenge))
                    .body().path("secret").asText();
            Reply confirmed = call("POST", "/api/auth/login/2fa/confirm", null,
                    Map.of("challengeToken", challenge, "code", currentCode(adminSecret)));
            assertEquals(200, confirmed.status(), confirmed.body().toString());
            return confirmed.body().path("token").asText();
        }
        forgetUsedCodes(ADMIN_EMAIL);
        Reply verified = call("POST", "/api/auth/login/2fa", null,
                Map.of("challengeToken", challenge, "code", currentCode(adminSecret)));
        assertEquals(200, verified.status(), verified.body().toString());
        return verified.body().path("token").asText();
    }

    /** The code an authenticator app with this secret shows right now. */
    protected static String currentCode(String secret) {
        return Totp.codeAt(secret, Totp.stepAt(Instant.now()));
    }

    /**
     * Each code works only once, and a new one comes every 30 seconds. Tests log in faster than
     * that, so they forget the last used code instead of waiting.
     */
    protected static void forgetUsedCodes(String email) {
        jdbc().update("update users set two_factor_last_step = null where email = ?", email);
    }

    /**
     * Signs up and verifies a brand-new member; returns their login token. Members confirm their
     * phone number by SMS before booking; PhoneVerificationApiTest covers that, so here it's marked
     * as confirmed directly.
     */
    protected static String newMember() throws Exception {
        String email = "member" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        Reply signUp = call("POST", "/api/auth/signup", null, Map.of(
                "fullName", "Test Member", "email", email, "phone", "0790000000", "password", "Secret1234"));
        assertEquals(201, signUp.status(), signUp.body().toString());
        Reply verified = call("POST", "/api/auth/verify", null,
                Map.of("email", email, "code", mailbox.latestVerificationCode(email)));
        assertEquals(200, verified.status(), verified.body().toString());
        jdbc().update("update users set phone_verified_at = ? where email = ?", LocalDateTime.now(AMMAN), email);
        return verified.body().path("token").asText();
    }

    protected static String memberEmail(String token) throws Exception {
        return call("GET", "/api/users/me", token, null).body().path("email").asText();
    }

    protected static long trainerId(String token, String branchName, String trainerName) throws Exception {
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

    protected static Reply book(String member, long trainer, String startTime) throws Exception {
        return call("POST", "/api/bookings", member, Map.of(
                "trainerId", trainer, "date", wednesday.toString(), "startTime", startTime, "durationMinutes", 60));
    }

    protected static long acceptedBooking(String member, long trainer, String startTime) throws Exception {
        Reply booked = book(member, trainer, startTime);
        assertEquals(201, booked.status(), booked.body().toString());
        long id = booked.body().path("id").asLong();
        String trainerToken = trainer == sara ? saraToken : linaToken;
        assertEquals(200, call("POST", "/api/trainer/requests/" + id + "/accept", trainerToken, null).status());
        return id;
    }

    protected static long paidBooking(String member, long trainer, String startTime) throws Exception {
        long id = acceptedBooking(member, trainer, startTime);
        String paymentIntent = paymentIntentOf(call("POST", "/api/bookings/" + id + "/payment", member, null));
        stripe.pay(paymentIntent, "visa", "4242");
        Reply paid = call("POST", "/api/bookings/" + id + "/payment/confirm", member, null);
        assertEquals("PAID", paid.body().path("status").asText(), paid.body().toString());
        return id;
    }

    /** Extracts the PaymentIntent id, the part of the client secret before "_secret_". */
    protected static String paymentIntentOf(Reply start) {
        String clientSecret = start.body().path("clientSecret").asText();
        assertNotNull(clientSecret);
        return clientSecret.substring(0, clientSecret.indexOf("_secret_"));
    }

    protected static JdbcTemplate jdbc() {
        return backend.getBean(JdbcTemplate.class);
    }
}
