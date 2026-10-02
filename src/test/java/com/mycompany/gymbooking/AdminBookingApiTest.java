package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The admin's bookings list, cancellations by the gym, and blocked times for branches and trainers. */
class AdminBookingApiTest extends ApiTestBase {

    private static String adminToken;
    /** Days other than {@link #wednesday}, so blocks in one test don't cancel another test's bookings. */
    private static LocalDate thursday;
    private static LocalDate monday;

    @BeforeAll
    static void setUp() throws Exception {
        adminToken = adminLogin();
        thursday = wednesday.plusDays(1);
        LocalDate day = LocalDate.now(AMMAN).plusDays(1);
        while (day.getDayOfWeek() != DayOfWeek.MONDAY) {
            day = day.plusDays(1);
        }
        monday = day;
    }

    @Test
    @DisplayName("admin sees every booking with contact details, filtered by trainer, branch and status")
    void listAndFilters() throws Exception {
        long requested = book(newMember(), sara, "08:00").body().path("id").asLong();
        long paid = paidBooking(newMember(), sara, "12:00");
        long withLina = book(newMember(), lina, "07:00").body().path("id").asLong();

        Reply all = call("GET", "/api/admin/bookings", adminToken, null);
        assertEquals(200, all.status(), all.body().toString());
        JsonNode row = find(all.body(), requested);
        assertEquals("REQUESTED", row.path("status").asText());
        assertTrue(row.path("memberEmail").asText().endsWith("@test.com"));
        assertEquals("0790000000", row.path("memberPhone").asText());
        assertEquals("sara.trainer@gym.com", row.path("trainerEmail").asText());
        assertTrue(row.path("gymCanCancel").asBoolean());
        assertEquals("Visa •••• 4242", find(all.body(), paid).path("payment").path("method").asText());

        List<Long> linaOnly = ids(call("GET", "/api/admin/bookings?trainerId=" + lina, adminToken, null).body());
        assertTrue(linaOnly.contains(withLina) && !linaOnly.contains(requested));
        long abdoun = find(all.body(), requested).path("branchId").asLong();
        List<Long> atAbdoun = ids(call("GET", "/api/admin/bookings?branchId=" + abdoun, adminToken, null).body());
        assertTrue(atAbdoun.contains(requested) && !atAbdoun.contains(withLina));
        List<Long> paidOnly = ids(call("GET", "/api/admin/bookings?status=PAID", adminToken, null).body());
        assertTrue(paidOnly.contains(paid) && !paidOnly.contains(requested));
        assertFalse(ids(call("GET", "/api/admin/bookings?past=true", adminToken, null).body()).contains(requested));

        assertEquals("INVALID_PARAMETER", call("GET", "/api/admin/bookings?status=SOON", adminToken, null).code());
        assertEquals(403, call("GET", "/api/admin/bookings", newMember(), null).status());
        assertEquals(403, call("GET", "/api/admin/bookings", saraToken, null).status());
    }

    @Test
    @DisplayName("the gym can cancel one booking, even a paid one inside 24 h: full refund, member and trainer emailed")
    void cancelOne() throws Exception {
        String member = newMember();
        long id = paidBooking(member, sara, "14:00");
        String paymentIntent = jdbc().queryForObject(
                "select provider_payment_id from payments where booking_id = ?", String.class, id);
        // Inside the last 24 h the member can't cancel any more, but the gym still can.
        jdbc().update("update bookings set refundable_until = ? where id = ?", LocalDate.now(AMMAN).minusDays(1).atStartOfDay(), id);
        assertEquals("TOO_LATE_TO_CANCEL", call("POST", "/api/bookings/" + id + "/cancel", member, null).code());

        Reply cancelled = call("POST", "/api/admin/bookings/" + id + "/cancel", adminToken, Map.of("reason", "Air conditioning repair"));
        assertEquals(200, cancelled.status(), cancelled.body().toString());
        assertEquals("CANCELLED", cancelled.body().path("status").asText());
        assertEquals("GYM", cancelled.body().path("cancelledBy").asText());
        assertEquals("Air conditioning repair", cancelled.body().path("cancellationNote").asText());
        assertEquals("REFUNDED", cancelled.body().path("payment").path("status").asText());
        assertFalse(cancelled.body().path("gymCanCancel").asBoolean());
        assertEquals(1, stripe.refundsFor(paymentIntent).size());

        String refundEmail = mailbox.latestBody(memberEmail(member), "Refund");
        assertTrue(refundEmail.contains("the gym had to cancel") && refundEmail.contains("Air conditioning repair"), refundEmail);
        String trainerEmail = mailbox.latestBody("sara.trainer@gym.com", "Session cancelled by the gym");
        assertTrue(trainerEmail.contains("refunded in full"), trainerEmail);

        assertEquals("BOOKING_NOT_CANCELLABLE", call("POST", "/api/admin/bookings/" + id + "/cancel", adminToken, null).code());
        assertEquals("BOOKING_NOT_FOUND", call("POST", "/api/admin/bookings/999999/cancel", adminToken, null).code());
        assertEquals("VALIDATION_FAILED", call("POST", "/api/admin/bookings/" + id + "/cancel", adminToken,
                Map.of("reason", "x".repeat(301))).code());
    }

    @Test
    @DisplayName("blocking a trainer's hours: preview, cancel and refund the bookings inside, hide the slots, unblock")
    void blockTrainerHours() throws Exception {
        String payer = newMember();
        long paidInside = bookOn(payer, sara, thursday, "10:00");
        accept(paidInside, saraToken);
        String paymentIntent = paymentIntentOf(call("POST", "/api/bookings/" + paidInside + "/payment", payer, null));
        stripe.pay(paymentIntent, "visa", "4242");
        call("POST", "/api/bookings/" + paidInside + "/payment/confirm", payer, null);
        long requestedInside = bookOn(newMember(), sara, thursday, "13:00");
        long outside = bookOn(newMember(), sara, thursday, "15:00");

        Map<String, Object> block = Map.of("trainerId", sara, "startDate", thursday.toString(), "endDate", thursday.toString(),
                "startTime", "09:00", "endTime", "14:00");
        Reply preview = call("POST", "/api/admin/blocked-times/preview", adminToken, block);
        assertEquals(200, preview.status(), preview.body().toString());
        assertEquals(2, preview.body().path("bookings").asInt());
        assertEquals(1, preview.body().path("paidBookings").asInt());

        Reply created = call("POST", "/api/admin/blocked-times", adminToken, block);
        assertEquals(201, created.status(), created.body().toString());
        assertEquals(2, created.body().path("cancelledBookings").asInt());
        assertEquals(1, created.body().path("refundedBookings").asInt());
        long blockId = created.body().path("blockedTime").path("id").asLong();
        assertEquals("Sara Haddad", created.body().path("blockedTime").path("trainerName").asText());
        assertFalse(created.body().path("blockedTime").path("allDay").asBoolean());

        assertEquals("CANCELLED", status(paidInside));
        assertEquals("CANCELLED", status(requestedInside));
        assertEquals("REQUESTED", status(outside));
        assertEquals(1, stripe.refundsFor(paymentIntent).size());
        assertEquals("Your trainer isn't available at that time.",
                call("GET", "/api/admin/bookings/" + requestedInside, adminToken, null).body().path("cancellationNote").asText());

        List<String> slots = slots(sara, thursday);
        assertTrue(slots.contains("08:00") && slots.contains("14:00"), slots.toString());
        assertFalse(slots.contains("08:30") || slots.contains("10:00") || slots.contains("13:00"), slots.toString());
        assertEquals("SLOT_NOT_AVAILABLE", call("POST", "/api/bookings", newMember(), Map.of(
                "trainerId", sara, "date", thursday.toString(), "startTime", "11:00", "durationMinutes", 60)).code());
        assertTrue(ids(call("GET", "/api/admin/blocked-times", adminToken, null).body()).contains(blockId));

        assertEquals(204, call("DELETE", "/api/admin/blocked-times/" + blockId, adminToken, null).status());
        assertTrue(slots(sara, thursday).contains("10:00"), "unblocked times are offered again");
        assertEquals("BLOCK_NOT_FOUND", call("DELETE", "/api/admin/blocked-times/" + blockId, adminToken, null).code());
    }

    @Test
    @DisplayName("closing a branch for a day: every trainer there is blocked and members see why")
    void closeBranch() throws Exception {
        long booking = bookOn(newMember(), lina, monday, "07:00");
        long sweifieh = call("GET", "/api/admin/bookings/" + booking, adminToken, null).body().path("branchId").asLong();

        Map<String, Object> closure = Map.of("branchId", sweifieh, "startDate", monday.toString(), "endDate", monday.toString(),
                "reason", "Eid holiday");
        assertEquals(1, call("POST", "/api/admin/blocked-times/preview", adminToken, closure).body().path("bookings").asInt());
        Reply created = call("POST", "/api/admin/blocked-times", adminToken, closure);
        assertEquals(201, created.status(), created.body().toString());
        assertTrue(created.body().path("blockedTime").path("allDay").asBoolean());
        assertEquals("Eid holiday", call("GET", "/api/admin/bookings/" + booking, adminToken, null)
                .body().path("cancellationNote").asText());

        JsonNode availability = call("GET", "/api/trainers/" + lina + "/availability?date=" + monday + "&duration=60",
                newMember(), null).body();
        assertTrue(availability.path("slots").isEmpty());
        assertEquals("The branch is closed on this day (Eid holiday).", availability.path("closedReason").asText());
        assertFalse(slots(sara, monday).isEmpty(), "other branches stay open");
    }

    @Test
    @DisplayName("bad blocked times are refused with clear messages")
    void blockValidation() throws Exception {
        String day = thursday.toString();
        assertEquals("INVALID_BLOCK", blockCode(Map.of("startDate", day, "endDate", day)));
        assertEquals("INVALID_BLOCK", blockCode(Map.of("branchId", 1, "trainerId", sara, "startDate", day, "endDate", day)));
        assertEquals("INVALID_DATES", blockCode(Map.of("trainerId", sara, "startDate", day, "endDate", thursday.minusDays(1).toString())));
        assertEquals("INVALID_DATES", blockCode(Map.of("trainerId", sara, "startDate", "2020-01-01", "endDate", day)));
        assertEquals("INVALID_DATES", blockCode(Map.of("trainerId", sara, "startDate", day, "endDate", thursday.plusYears(2).toString())));
        assertEquals("INVALID_TIMES", blockCode(Map.of("trainerId", sara, "startDate", day, "endDate", day, "startTime", "09:00")));
        assertEquals("INVALID_TIMES", blockCode(Map.of("trainerId", sara, "startDate", day, "endDate", day,
                "startTime", "14:00", "endTime", "09:00")));
        assertEquals("BRANCH_NOT_FOUND", blockCode(Map.of("branchId", 999999, "startDate", day, "endDate", day)));
        assertEquals("VALIDATION_FAILED", blockCode(Map.of("trainerId", sara, "endDate", day)));
        assertEquals(403, call("POST", "/api/admin/blocked-times", saraToken,
                Map.of("trainerId", sara, "startDate", day, "endDate", day)).status());
    }

    private static String blockCode(Map<String, Object> body) throws Exception {
        return call("POST", "/api/admin/blocked-times", adminToken, new HashMap<>(body)).code();
    }

    private static long bookOn(String member, long trainer, LocalDate date, String startTime) throws Exception {
        Reply booked = call("POST", "/api/bookings", member, Map.of(
                "trainerId", trainer, "date", date.toString(), "startTime", startTime, "durationMinutes", 60));
        assertEquals(201, booked.status(), booked.body().toString());
        return booked.body().path("id").asLong();
    }

    private static void accept(long bookingId, String trainerToken) throws Exception {
        assertEquals(200, call("POST", "/api/trainer/requests/" + bookingId + "/accept", trainerToken, null).status());
    }

    private static String status(long bookingId) throws Exception {
        return call("GET", "/api/admin/bookings/" + bookingId, adminToken, null).body().path("status").asText();
    }

    private static List<String> slots(long trainer, LocalDate date) throws Exception {
        List<String> starts = new ArrayList<>();
        JsonNode body = call("GET", "/api/trainers/" + trainer + "/availability?date=" + date + "&duration=60",
                newMember(), null).body();
        body.path("slots").forEach(slot -> starts.add(slot.path("start").asText()));
        return starts;
    }

    private static JsonNode find(JsonNode list, long id) {
        for (JsonNode row : list) {
            if (row.path("id").asLong() == id) {
                return row;
            }
        }
        throw new AssertionError("No booking " + id + " in " + list);
    }

    private static List<Long> ids(JsonNode list) {
        List<Long> ids = new ArrayList<>();
        list.forEach(row -> ids.add(row.path("id").asLong()));
        return ids;
    }
}
