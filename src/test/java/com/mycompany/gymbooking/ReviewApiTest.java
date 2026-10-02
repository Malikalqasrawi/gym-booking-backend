package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rating sessions, the trainers' averages, trainer replies and the admin hiding reviews. */
class ReviewApiTest extends ApiTestBase {

    @Test
    @DisplayName("a member rates a session once, after it took place, and the trainer's average shows everywhere")
    void rateSession() throws Exception {
        String member = newMember();
        long booking = paidBooking(member, sara, "09:00");
        assertEquals("CANNOT_REVIEW", rate(member, booking, 4, null).code(), "the session hasn't happened yet");

        tookPlace(booking, 1);
        JsonNode mine = myBooking(member, booking);
        assertTrue(mine.path("canReview").asBoolean());
        assertTrue(mine.path("rating").isNull());

        assertEquals("VALIDATION_FAILED", rate(member, booking, 0, null).code());
        assertEquals("VALIDATION_FAILED", rate(member, booking, 4, "x".repeat(501)).code());
        assertEquals("BOOKING_NOT_FOUND", rate(newMember(), booking, 1, "Not my session").code());

        Reply rated = rate(member, booking, 4, "  Great session, very patient.  ");
        assertEquals(201, rated.status(), rated.body().toString());
        assertEquals("Test M.", rated.body().path("memberName").asText(), "first name and initial only");
        assertEquals("Great session, very patient.", rated.body().path("comment").asText());
        assertFalse(rated.body().has("hidden"), "only the admin sees that");
        assertEquals("ALREADY_REVIEWED", rate(member, booking, 5, "Changed my mind").code(), "reviews are final");
        mine = myBooking(member, booking);
        assertEquals(4, mine.path("rating").asInt());
        assertFalse(mine.path("canReview").asBoolean());

        String other = newMember();
        long second = paidBooking(other, sara, "10:00");
        tookPlace(second, 2);
        assertEquals(201, rate(other, second, 5, null).status());

        JsonNode profile = call("GET", "/api/trainers/" + sara, member, null).body();
        assertEquals(4.5, profile.path("averageRating").asDouble());
        assertEquals(2, profile.path("reviewCount").asInt());
        JsonNode inCategory = trainerIn(call("GET", "/api/trainers?category=STRENGTH", member, null).body(), sara);
        assertEquals(4.5, inCategory.path("averageRating").asDouble(), "lists show it too");
        long yousef = trainerId(member, "Abdoun Branch", "Yousef Al-Masri");
        assertTrue(trainerIn(call("GET", "/api/trainers", member, null).body(), yousef).path("averageRating").isNull(),
                "no reviews yet");

        JsonNode reviews = call("GET", "/api/trainers/" + sara + "/reviews", member, null).body();
        assertEquals(2, reviews.path("reviews").size());
        assertEquals(5, reviews.path("reviews").get(0).path("rating").asInt(), "newest first");
    }

    @Test
    @DisplayName("sessions can be rated for 30 days")
    void ratingWindow() throws Exception {
        String member = newMember();
        long booking = paidBooking(member, sara, "11:00");
        tookPlace(booking, 31);
        Reply late = rate(member, booking, 3, null);
        assertEquals("CANNOT_REVIEW", late.code());
        assertTrue(late.body().path("message").asText().contains("for 30 days"), late.body().toString());
        assertFalse(myBooking(member, booking).path("canReview").asBoolean());
    }

    @Test
    @DisplayName("the trainer answers a review, and the admin can hide it with a reason the member is emailed")
    void replyAndHide() throws Exception {
        String member = newMember();
        String email = memberEmail(member);
        long booking = paidBooking(member, lina, "08:00");
        tookPlace(booking, 1);
        long review = rate(member, booking, 2, "Late and unprepared.").body().path("id").asLong();

        JsonNode linaReviews = call("GET", "/api/trainer/reviews", linaToken, null).body();
        assertEquals(review, linaReviews.path("reviews").get(0).path("id").asLong());
        assertEquals("REVIEW_NOT_FOUND", reply(saraToken, review, "Not my session").code());
        assertEquals("VALIDATION_FAILED", reply(linaToken, review, " ").code());
        assertEquals(200, reply(linaToken, review, "Sorry about that.").status());
        assertEquals("Sorry, the bus was late.", reply(linaToken, review, "Sorry, the bus was late.").body().path("reply").asText(),
                "a new answer replaces the old one");
        assertEquals("Sorry, the bus was late.",
                call("GET", "/api/trainers/" + lina + "/reviews", member, null).body().path("reviews").get(0).path("reply").asText());

        assertEquals(403, call("POST", "/api/admin/reviews/" + review + "/hide", member, Map.of("reason", "x")).status());
        String admin = adminLogin();
        assertEquals("VALIDATION_FAILED", call("POST", "/api/admin/reviews/" + review + "/hide", admin, Map.of("reason", "")).code());
        Reply hidden = call("POST", "/api/admin/reviews/" + review + "/hide", admin, Map.of("reason", "Insulting language"));
        assertTrue(hidden.body().path("hidden").asBoolean(), hidden.body().toString());
        assertEquals("Test Member", hidden.body().path("memberName").asText(), "the admin sees the full name");

        JsonNode profile = call("GET", "/api/trainers/" + lina + "/reviews", member, null).body();
        assertEquals(0, profile.path("reviewCount").asInt());
        assertTrue(profile.path("averageRating").isNull());
        assertEquals(0, profile.path("reviews").size());
        assertEquals(1, mailbox.count(email, "Your review of Lina Nasser was hidden"));
        assertTrue(mailbox.latestBody(email, "Your review of").contains("  Reason:    Insulting language"));
        assertEquals("REVIEW_HIDDEN", reply(linaToken, review, "Thanks").code());
        assertEquals(1, call("GET", "/api/admin/reviews?hidden=true", admin, null).body().size());

        assertEquals(200, call("POST", "/api/admin/reviews/" + review + "/show", admin, null).status());
        assertEquals(1, call("GET", "/api/trainers/" + lina + "/reviews", member, null).body().path("reviewCount").asInt());
    }

    private static Reply rate(String member, long booking, int stars, String comment) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("rating", stars);
        body.put("comment", comment);
        return call("POST", "/api/bookings/" + booking + "/review", member, body);
    }

    private static Reply reply(String trainer, long review, String text) throws Exception {
        return call("PUT", "/api/trainer/reviews/" + review + "/reply", trainer, Map.of("reply", text));
    }

    /** Moves a paid session into the past instead of waiting for it. */
    private static void tookPlace(long booking, int daysAgo) {
        jdbc().update("update bookings set session_date = ? where id = ?", LocalDate.now(AMMAN).minusDays(daysAgo), booking);
    }

    private static JsonNode myBooking(String member, long booking) throws Exception {
        return call("GET", "/api/bookings/" + booking, member, null).body();
    }

    private static JsonNode trainerIn(JsonNode trainers, long id) {
        for (JsonNode trainer : trainers) {
            if (trainer.path("id").asLong() == id) {
                return trainer;
            }
        }
        throw new AssertionError("Trainer " + id + " is not in the list");
    }
}
