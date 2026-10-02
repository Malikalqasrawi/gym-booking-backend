package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Access and refresh tokens, logging out, and changing the password. */
class SessionApiTest extends ApiTestBase {

    /** One device's session: the access token and the refresh token from a login. */
    private record Session(String access, String refresh) {
    }

    @Test
    @DisplayName("refreshing replaces the refresh token; a retired one coming back later ends every session")
    void refreshRotates() throws Exception {
        String email = memberEmail(newMember());
        Session first = login(email);
        assertFalse(first.refresh().isBlank());

        Session second = refresh(first.refresh());
        assertNotEquals(first.refresh(), second.refresh());
        assertEquals(200, call("GET", "/api/users/me", second.access(), null).status());

        // A quick retry with the old token (e.g. a lost response) is refused but logs nobody out.
        assertEquals("SESSION_ENDED", refreshReply(first.refresh()).code());
        Session third = refresh(second.refresh());

        // Later on, a retired token means someone may have copied it: every session ends.
        jdbc().update("update refresh_tokens set revoked_at = ? where revoked_at is not null"
                + " and user_id = (select id from users where email = ?)", LocalDateTime.now(AMMAN).minusMinutes(10), email);
        assertEquals("SESSION_ENDED", refreshReply(first.refresh()).code());
        assertEquals(401, call("GET", "/api/users/me", third.access(), null).status());
        assertEquals("SESSION_ENDED", refreshReply(third.refresh()).code());
    }

    @Test
    @DisplayName("logout ends only this device's session")
    void logoutOneDevice() throws Exception {
        String email = memberEmail(newMember());
        Session phone = login(email);
        Session tablet = login(email);

        assertEquals(204, call("POST", "/api/auth/logout", null, Map.of("refreshToken", phone.refresh())).status());
        assertEquals("SESSION_ENDED", refreshReply(phone.refresh()).code());
        assertEquals(200, refreshReply(tablet.refresh()).status());
        assertEquals(204, call("POST", "/api/auth/logout", null, Map.of("refreshToken", "unknown")).status(),
                "logging out twice or with an unknown token is harmless");
    }

    @Test
    @DisplayName("log out of all devices ends every session right away")
    void logoutEverywhere() throws Exception {
        String email = memberEmail(newMember());
        Session phone = login(email);
        Session tablet = login(email);

        assertEquals(204, call("POST", "/api/users/me/logout-all", phone.access(), null).status());
        assertEquals(401, call("GET", "/api/users/me", phone.access(), null).status());
        assertEquals(401, call("GET", "/api/users/me", tablet.access(), null).status());
        assertEquals("SESSION_ENDED", refreshReply(tablet.refresh()).code());
        assertEquals(200, call("GET", "/api/users/me", login(email).access(), null).status(), "logging in again works");
    }

    @Test
    @DisplayName("change password: needs the current one, keeps this device logged in and logs out the others")
    void changePassword() throws Exception {
        String email = memberEmail(newMember());
        Session phone = login(email);
        Session tablet = login(email);

        assertEquals("WRONG_PASSWORD", changePassword(phone, "Wrong1234", "NewPass123").code());
        assertEquals("SAME_PASSWORD", changePassword(phone, "Secret1234", "Secret1234").code());
        assertEquals("VALIDATION_FAILED", changePassword(phone, "Secret1234", "short").code());

        Reply changed = changePassword(phone, "Secret1234", "NewPass123");
        assertEquals(200, changed.status(), changed.body().toString());
        String newAccess = changed.body().path("token").asText();
        assertEquals(200, call("GET", "/api/users/me", newAccess, null).status());
        assertEquals(401, call("GET", "/api/users/me", tablet.access(), null).status());
        assertEquals("SESSION_ENDED", refreshReply(tablet.refresh()).code());

        assertEquals("INVALID_CREDENTIALS", call("POST", "/api/auth/login", null,
                Map.of("email", email, "password", "Secret1234")).code());
        assertEquals(200, call("POST", "/api/auth/login", null, Map.of("email", email, "password", "NewPass123")).status());
    }

    @Test
    @DisplayName("an expired refresh token can't be used")
    void expiredRefreshToken() throws Exception {
        String email = memberEmail(newMember());
        Session session = login(email);
        jdbc().update("update refresh_tokens set expires_at = ? where user_id = (select id from users where email = ?)",
                LocalDateTime.now(AMMAN).minusDays(1), email);
        assertEquals("SESSION_ENDED", refreshReply(session.refresh()).code());
        assertEquals("VALIDATION_FAILED", call("POST", "/api/auth/refresh", null, Map.of("refreshToken", "")).code());
    }

    private static Session login(String email) throws Exception {
        Reply reply = call("POST", "/api/auth/login", null, Map.of("email", email, "password", "Secret1234"));
        assertEquals(200, reply.status(), reply.body().toString());
        return new Session(reply.body().path("token").asText(), reply.body().path("refreshToken").asText());
    }

    private static Reply refreshReply(String refreshToken) throws Exception {
        return call("POST", "/api/auth/refresh", null, Map.of("refreshToken", refreshToken));
    }

    private static Session refresh(String refreshToken) throws Exception {
        Reply reply = refreshReply(refreshToken);
        assertEquals(200, reply.status(), reply.body().toString());
        return new Session(reply.body().path("token").asText(), reply.body().path("refreshToken").asText());
    }

    private static Reply changePassword(Session session, String current, String next) throws Exception {
        return call("POST", "/api/users/me/password", session.access(),
                Map.of("currentPassword", current, "newPassword", next));
    }
}
