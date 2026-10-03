package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Admin trainer management: invites, editing, schedules, deactivation and access rules. */
class AdminTrainerApiTest extends ApiTestBase {

    private static final AtomicInteger TRAINER_NUMBER = new AtomicInteger();

    private static String adminToken;

    @BeforeAll
    static void logInAdmin() throws Exception {
        adminToken = adminLogin();
    }

    @Test
    @DisplayName("only admins can manage trainers")
    void adminsOnly() throws Exception {
        assertEquals(401, call("GET", "/api/admin/trainers", null, null).status());
        assertEquals(403, call("GET", "/api/admin/trainers", newMember(), null).status());
        assertEquals(403, call("GET", "/api/admin/trainers", saraToken, null).status());
        assertEquals(403, call("POST", "/api/admin/trainers", saraToken,
                trainerBody(nextEmail(), branchId("Khalda Branch"))).status());

        Reply list = call("GET", "/api/admin/trainers", adminToken, null);
        assertEquals(200, list.status());
        JsonNode saraRow = find(list.body(), "sara.trainer@gym.com");
        assertEquals("ACTIVE", saraRow.path("status").asText());
        assertEquals("Abdoun Branch", saraRow.path("branchName").asText());
        assertFalse(saraRow.path("schedule").isEmpty());
    }

    @Test
    @DisplayName("invite: admin adds a trainer → invite email → trainer sets a password → bookable once scheduled")
    void inviteFlow() throws Exception {
        String email = nextEmail();
        long khalda = branchId("Khalda Branch");
        Reply created = call("POST", "/api/admin/trainers", adminToken, trainerBody(email, khalda));
        assertEquals(201, created.status(), created.body().toString());
        long id = created.body().path("id").asLong();
        assertEquals("INVITED", created.body().path("status").asText());
        assertFalse(created.body().path("inviteExpiresAt").isNull());

        String member = newMember();
        assertFalse(listedAt(member, khalda, id), "invited trainers are hidden from members");
        assertEquals(404, call("GET", "/api/trainers/" + id, member, null).status());

        Reply login = call("POST", "/api/auth/login", null, Map.of("email", email, "password", "Guess1234"));
        assertEquals("INVALID_CREDENTIALS", login.code());

        String code = mailbox.latestInviteCode(email);
        assertEquals("INVALID_CODE", call("POST", "/api/auth/verify", null,
                Map.of("email", email, "code", code, "password", "Secret1234")).code(), "the invite can't be used without setting a password");

        Reply wrong = call("POST", "/api/auth/accept-invite", null,
                Map.of("email", email, "code", code.equals("000000") ? "111111" : "000000", "password", "Boxing2026"));
        assertEquals("INVALID_CODE", wrong.code());

        Reply weak = call("POST", "/api/auth/accept-invite", null, Map.of("email", email, "code", code, "password", "short"));
        assertEquals(400, weak.status());

        Reply accepted = call("POST", "/api/auth/accept-invite", null,
                Map.of("email", email, "code", code, "password", "Boxing2026"));
        assertEquals(200, accepted.status(), accepted.body().toString());
        assertEquals("TRAINER", accepted.body().path("user").path("role").asText());
        login(email, "Boxing2026");

        Reply again = call("POST", "/api/auth/accept-invite", null,
                Map.of("email", email, "code", code, "password", "Other2026"));
        assertEquals("INVALID_CODE", again.code(), "an invite works once");

        assertEquals("ACTIVE", call("GET", "/api/admin/trainers/" + id, adminToken, null).body().path("status").asText());
        assertTrue(listedAt(member, khalda, id), "joined trainers are visible");
        assertEquals(0, slotsOnWednesday(member, id).size(), "no schedule yet");

        Reply scheduled = call("PUT", "/api/admin/trainers/" + id + "/schedule", adminToken, Map.of("blocks", List.of(
                Map.of("dayOfWeek", "WEDNESDAY", "startTime", "09:00", "endTime", "12:00"),
                Map.of("dayOfWeek", "SUNDAY", "startTime", "16:00", "endTime", "20:00"))));
        assertEquals(200, scheduled.status(), scheduled.body().toString());
        assertEquals("SUNDAY", scheduled.body().path("schedule").get(0).path("dayOfWeek").asText(), "week starts on Sunday");

        JsonNode slots = slotsOnWednesday(member, id);
        assertEquals("09:00", slots.get(0).path("start").asText());
        assertEquals("11:00", slots.get(slots.size() - 1).path("start").asText());
    }

    @Test
    @DisplayName("bad input: duplicate email, unknown branch, missing fields, overlapping schedule")
    void validation() throws Exception {
        long khalda = branchId("Khalda Branch");
        assertEquals("EMAIL_TAKEN", call("POST", "/api/admin/trainers", adminToken,
                trainerBody("sara.trainer@gym.com", khalda)).code());
        assertEquals("BRANCH_NOT_FOUND", call("POST", "/api/admin/trainers", adminToken,
                trainerBody(nextEmail(), 999_999)).code());

        Map<String, Object> incomplete = trainerBody(nextEmail(), khalda);
        incomplete.remove("hourlyRate");
        incomplete.put("phone", "");
        Reply invalid = call("POST", "/api/admin/trainers", adminToken, incomplete);
        assertEquals(400, invalid.status());
        assertTrue(invalid.body().path("fieldErrors").has("hourlyRate"));
        assertEquals("Phone number is required", invalid.body().path("fieldErrors").path("phone").asText(),
                "a blank field reports 'required', not its format rule");

        long id = call("POST", "/api/admin/trainers", adminToken, trainerBody(nextEmail(), khalda)).body().path("id").asLong();
        Reply overlapping = call("PUT", "/api/admin/trainers/" + id + "/schedule", adminToken, Map.of("blocks", List.of(
                Map.of("dayOfWeek", "MONDAY", "startTime", "08:00", "endTime", "12:00"),
                Map.of("dayOfWeek", "MONDAY", "startTime", "11:00", "endTime", "14:00"))));
        assertEquals("INVALID_SCHEDULE", overlapping.code());
        assertTrue(overlapping.body().path("message").asText().startsWith("Monday"));

        Reply backwards = call("PUT", "/api/admin/trainers/" + id + "/schedule", adminToken, Map.of("blocks", List.of(
                Map.of("dayOfWeek", "MONDAY", "startTime", "14:00", "endTime", "09:00"))));
        assertEquals("INVALID_SCHEDULE", backwards.code());

        assertEquals("TRAINER_NOT_FOUND", call("GET", "/api/admin/trainers/999999", adminToken, null).code());
    }

    @Test
    @DisplayName("editing: profile and rate change, and a new email gets a fresh invite")
    void editing() throws Exception {
        long khalda = branchId("Khalda Branch");
        long jubeiha = branchId("Jubeiha Branch");
        String oldEmail = nextEmail();
        long id = call("POST", "/api/admin/trainers", adminToken, trainerBody(oldEmail, khalda)).body().path("id").asLong();
        String oldCode = mailbox.latestInviteCode(oldEmail);

        String newEmail = nextEmail();
        Map<String, Object> changes = trainerBody(newEmail, jubeiha);
        changes.put("hourlyRate", 30);
        changes.put("tags", List.of("Boxing", "Kids"));
        Reply updated = call("PUT", "/api/admin/trainers/" + id, adminToken, changes);
        assertEquals(200, updated.status(), updated.body().toString());
        assertEquals("Jubeiha Branch", updated.body().path("branchName").asText());
        assertEquals(30, updated.body().path("hourlyRate").asInt());
        assertEquals("Kids", updated.body().path("tags").get(1).asText());

        String newCode = mailbox.latestInviteCode(newEmail);
        assertEquals("INVALID_CODE", call("POST", "/api/auth/accept-invite", null,
                Map.of("email", oldEmail, "code", oldCode, "password", "Boxing2026")).code());
        assertEquals(200, call("POST", "/api/auth/accept-invite", null,
                Map.of("email", newEmail, "code", newCode, "password", "Boxing2026")).status());

        assertEquals("INVITE_ALREADY_ACCEPTED", call("POST", "/api/admin/trainers/" + id + "/invite", adminToken, null).code());
        assertEquals("EMAIL_TAKEN", call("PUT", "/api/admin/trainers/" + id, adminToken,
                trainerBody("lina.trainer@gym.com", jubeiha)).code());
    }

    @Test
    @DisplayName("deactivating cancels upcoming bookings (paid ones refunded in full), hides the trainer and blocks login")
    void deactivation() throws Exception {
        String email = nextEmail();
        long id = call("POST", "/api/admin/trainers", adminToken, trainerBody(email, branchId("Shmeisani Branch")))
                .body().path("id").asLong();
        Reply joined = call("POST", "/api/auth/accept-invite", null,
                Map.of("email", email, "code", mailbox.latestInviteCode(email), "password", "Boxing2026"));
        String trainerToken = joined.body().path("token").asText();
        assertEquals(200, call("PUT", "/api/admin/trainers/" + id + "/schedule", adminToken, Map.of("blocks", List.of(
                Map.of("dayOfWeek", "WEDNESDAY", "startTime", "08:00", "endTime", "16:00")))).status());

        String payer = newMember();
        long paid = book(payer, id, "10:00").body().path("id").asLong();
        assertEquals(200, call("POST", "/api/trainer/requests/" + paid + "/accept", trainerToken, null).status());
        String paymentIntent = paymentIntentOf(call("POST", "/api/bookings/" + paid + "/payment", payer, null));
        stripe.pay(paymentIntent, "visa", "4242");
        assertEquals("PAID", call("POST", "/api/bookings/" + paid + "/payment/confirm", payer, null).body().path("status").asText());

        String requester = newMember();
        long requested = book(requester, id, "13:00").body().path("id").asLong();

        assertEquals(2, call("GET", "/api/admin/trainers/" + id, adminToken, null).body().path("upcomingBookings").asInt());

        Reply deactivated = call("POST", "/api/admin/trainers/" + id + "/deactivate", adminToken, Map.of("reason", "Moved abroad"));
        assertEquals(200, deactivated.status(), deactivated.body().toString());
        assertEquals(2, deactivated.body().path("cancelledBookings").asInt());
        assertEquals(1, deactivated.body().path("refundedBookings").asInt());
        assertEquals("DEACTIVATED", deactivated.body().path("trainer").path("status").asText());
        assertEquals(0, deactivated.body().path("trainer").path("upcomingBookings").asInt());

        Reply paidView = call("GET", "/api/bookings/" + paid, payer, null);
        assertEquals("CANCELLED", paidView.body().path("status").asText());
        assertEquals("GYM", paidView.body().path("cancelledBy").asText());
        assertEquals("Moved abroad", paidView.body().path("cancellationNote").asText());
        assertEquals("REFUNDED", paidView.body().path("payment").path("status").asText());
        assertEquals(1, stripe.refundsFor(paymentIntent).size());
        String refundEmail = mailbox.latestBody(memberEmail(payer), "Refund");
        assertTrue(refundEmail.contains("the gym had to cancel") && refundEmail.contains("Moved abroad"), refundEmail);

        assertEquals("CANCELLED", call("GET", "/api/bookings/" + requested, requester, null).body().path("status").asText());
        assertTrue(mailbox.latestBody(memberEmail(requester), "Your session was cancelled").contains("Nothing was charged"));

        Reply oldToken = call("GET", "/api/trainer/requests", trainerToken, null);
        assertEquals(401, oldToken.status(), "existing token stops working");
        assertEquals("Your session has ended. Please log in again.", oldToken.body().path("message").asText());
        assertEquals("SESSION_ENDED", call("POST", "/api/auth/refresh", null,
                Map.of("refreshToken", joined.body().path("refreshToken").asText())).code(), "and can't be renewed");
        assertEquals("ACCOUNT_DEACTIVATED", call("POST", "/api/auth/login", null,
                Map.of("email", email, "password", "Boxing2026")).code());
        assertEquals(404, call("GET", "/api/trainers/" + id, payer, null).status());
        assertEquals("TRAINER_NOT_BOOKABLE", book(payer, id, "11:00").code());
        assertEquals("TRAINER_ALREADY_DEACTIVATED", call("POST", "/api/admin/trainers/" + id + "/deactivate", adminToken, null).code());
        assertEquals("TRAINER_DEACTIVATED", call("POST", "/api/admin/trainers/" + id + "/invite", adminToken, null).code());

        Reply reactivated = call("POST", "/api/admin/trainers/" + id + "/reactivate", adminToken, null);
        assertEquals("ACTIVE", reactivated.body().path("status").asText());
        login(email, "Boxing2026");
        assertEquals("TRAINER_ALREADY_ACTIVE", call("POST", "/api/admin/trainers/" + id + "/reactivate", adminToken, null).code());
    }

    @Test
    @DisplayName("a branch with trainers or bookings can't be deleted (409 BRANCH_IN_USE), an unused one can")
    void branchInUse() throws Exception {
        Reply inUse = call("DELETE", "/api/branches/" + branchId("Abdoun Branch"), adminToken, null);
        assertEquals(409, inUse.status());
        assertEquals("BRANCH_IN_USE", inUse.code());

        Reply created = call("POST", "/api/branches", adminToken, Map.of(
                "name", "Test Branch", "address", "Rainbow Street", "city", "Amman",
                "latitude", 31.95, "longitude", 35.92, "openingTime", "06:00", "closingTime", "22:00"));
        assertEquals(201, created.status(), created.body().toString());
        assertEquals(204, call("DELETE", "/api/branches/" + created.body().path("id").asLong(), adminToken, null).status());
    }

    private static String nextEmail() {
        return "coach" + TRAINER_NUMBER.incrementAndGet() + "@test.com";
    }

    private static Map<String, Object> trainerBody(String email, long branchId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Rami Khalil");
        body.put("email", email);
        body.put("phone", "0791234567");
        body.put("branchId", branchId);
        body.put("category", "BOXING");
        body.put("gender", "MALE");
        body.put("hourlyRate", 25);
        body.put("specialty", "Boxing");
        body.put("bio", "Former national team boxer.");
        body.put("yearsOfExperience", 8);
        body.put("languages", "Arabic, English");
        body.put("tags", List.of("Boxing", "Cardio"));
        body.put("certifications", List.of("Boxing Coach Level 1"));
        return body;
    }

    private static long branchId(String name) throws Exception {
        for (JsonNode branch : call("GET", "/api/branches", adminToken, null).body()) {
            if (branch.path("name").asText().equals(name)) {
                return branch.path("id").asLong();
            }
        }
        throw new AssertionError("No branch " + name);
    }

    private static JsonNode find(JsonNode trainers, String email) {
        for (JsonNode trainer : trainers) {
            if (trainer.path("email").asText().equals(email)) {
                return trainer;
            }
        }
        throw new AssertionError(email + " not in the admin list");
    }

    private static boolean listedAt(String token, long branchId, long trainerId) throws Exception {
        for (JsonNode trainer : call("GET", "/api/branches/" + branchId + "/trainers", token, null).body()) {
            if (trainer.path("id").asLong() == trainerId) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode slotsOnWednesday(String token, long trainerId) throws Exception {
        Reply availability = call("GET", "/api/trainers/" + trainerId + "/availability?date=" + wednesday + "&duration=60",
                token, null);
        assertEquals(200, availability.status(), availability.body().toString());
        return availability.body().path("slots");
    }
}
