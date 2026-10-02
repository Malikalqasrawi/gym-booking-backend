package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.notification.NotificationSender;
import com.mycompany.gymbooking.support.CapturingNotificationSender;
import com.mycompany.gymbooking.support.FakeStripe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
 * class), FakeStripe instead of Stripe and CapturingNotificationSender instead of email. Tests use
 * their own members and time slots so they stay independent within a class.
 */
abstract class ApiTestBase {

    protected static final String WEBHOOK_SECRET = "whsec_test_only";
    protected static final ZoneId AMMAN = ZoneId.of("Asia/Amman");
    protected static final ObjectMapper JSON = new ObjectMapper();
    protected static final HttpClient HTTP = HttpClient.newHttpClient();
    protected static final AtomicInteger MEMBER_NUMBER = new AtomicInteger();

    protected static FakeStripe stripe;
    protected static CapturingNotificationSender mailbox;
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
        mailbox = new CapturingNotificationSender();

        byte[] jwtSecret = new byte[64];
        new SecureRandom().nextBytes(jwtSecret);

        backend = new SpringApplicationBuilder(Gymbooking.class)
                // Notifications mode "test" disables the real senders, leaving only the capturing one
                .initializers(context -> ((GenericApplicationContext) context)
                        .registerBean(NotificationSender.class, () -> mailbox))
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

    /** Signs up and verifies a brand-new member; returns their login token. */
    protected static String newMember() throws Exception {
        String email = "member" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        Reply signUp = call("POST", "/api/auth/signup", null, Map.of(
                "fullName", "Test Member", "email", email, "phone", "0790000000", "password", "Secret1234"));
        assertEquals(201, signUp.status(), signUp.body().toString());
        Reply verified = call("POST", "/api/auth/verify", null,
                Map.of("email", email, "code", mailbox.latestVerificationCode(email)));
        assertEquals(200, verified.status(), verified.body().toString());
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
