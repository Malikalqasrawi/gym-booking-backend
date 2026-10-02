package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Two-factor authentication with an authenticator app: required for admins, optional for others. */
class TwoFactorApiTest extends ApiTestBase {

    private static final String PASSWORD = "Secret1234";

    /** Two-factor authentication just turned on: the app's secret, the code that confirmed it, and the recovery codes. */
    private record Enabled(String secret, String confirmCode, List<String> recoveryCodes) {
    }

    @Test
    @DisplayName("an admin sets up an authenticator app at the first login, then logs in with codes")
    void adminSetupAtFirstLogin() throws Exception {
        resetAdminTwoFactor();   // the other admin test may have set it up already
        Reply login = passwordStep(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertEquals("SETUP_REQUIRED", login.body().path("twoFactor").asText());
        assertTrue(login.body().path("token").isMissingNode(), "no session before the app is set up");
        String challenge = login.body().path("challengeToken").asText();
        assertEquals(401, call("GET", "/api/users/me", challenge, null).status(), "a challenge isn't an access token");
        assertEquals("LOGIN_EXPIRED", codeStep(challenge, "123456").code(), "nothing to check a code against yet");

        Reply setup = call("POST", "/api/auth/login/2fa/setup", null, Map.of("challengeToken", challenge));
        assertEquals(200, setup.status(), setup.body().toString());
        String secret = setup.body().path("secret").asText();
        assertEquals(32, secret.length());
        assertTrue(setup.body().path("otpauthUri").asText().startsWith("otpauth://totp/Gym%20Booking:admin%40gym.com?secret=" + secret));

        assertEquals("INVALID_TWO_FACTOR_CODE", confirmAtLogin(challenge, wrongCode(secret)).code());
        Reply confirmed = confirmAtLogin(challenge, currentCode(secret));
        assertEquals(200, confirmed.status(), confirmed.body().toString());
        assertEquals(8, confirmed.body().path("recoveryCodes").size());
        String session = confirmed.body().path("token").asText();
        assertEquals(200, call("GET", "/api/admin/bookings", session, null).status());
        assertTrue(call("GET", "/api/users/me", session, null).body().path("twoFactorEnabled").asBoolean());

        // From now on: password, then a code.
        Reply next = passwordStep(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertEquals("CODE_REQUIRED", next.body().path("twoFactor").asText());
        forgetUsedCodes(ADMIN_EMAIL);
        Reply loggedIn = codeStep(next.body().path("challengeToken").asText(), currentCode(secret));
        assertEquals(200, loggedIn.status(), loggedIn.body().toString());
        assertEquals("ADMIN", loggedIn.body().path("user").path("role").asText());

        Reply turnOff = call("POST", "/api/users/me/2fa/disable", session,
                Map.of("password", ADMIN_PASSWORD, "code", confirmed.body().path("recoveryCodes").get(0).asText()));
        assertEquals("TWO_FACTOR_REQUIRED", turnOff.code());
        assertEquals(403, turnOff.status());
    }

    @Test
    @DisplayName("admin sessions from before two-factor login was required are ended")
    void oldAdminSessionsEnd() throws Exception {
        resetAdminTwoFactor();
        String challenge = passwordStep(ADMIN_EMAIL, ADMIN_PASSWORD).body().path("challengeToken").asText();
        String secret = call("POST", "/api/auth/login/2fa/setup", null, Map.of("challengeToken", challenge))
                .body().path("secret").asText();
        Reply login = confirmAtLogin(challenge, currentCode(secret));
        assertEquals(200, login.status(), login.body().toString());
        String access = login.body().path("token").asText();
        String refresh = login.body().path("refreshToken").asText();
        assertEquals(200, call("GET", "/api/admin/bookings", access, null).status());

        // Like an admin who logged in with only a password before this version.
        resetAdminTwoFactor();
        assertEquals(401, call("GET", "/api/admin/bookings", access, null).status());
        assertEquals("SESSION_ENDED", call("POST", "/api/auth/refresh", null, Map.of("refreshToken", refresh)).code());
        assertEquals("SETUP_REQUIRED", passwordStep(ADMIN_EMAIL, ADMIN_PASSWORD).body().path("twoFactor").asText());
    }

    @Test
    @DisplayName("a member turns two-factor on, logs in with a code or a recovery code, and turns it off")
    void memberTurnsItOnAndOff() throws Exception {
        String session = newMember();
        String email = memberEmail(session);
        assertFalse(call("GET", "/api/users/me", session, null).body().path("twoFactorEnabled").asBoolean());

        assertEquals("VALIDATION_FAILED", call("POST", "/api/users/me/2fa/setup", session, Map.of()).code());
        assertEquals("WRONG_PASSWORD", call("POST", "/api/users/me/2fa/setup", session, Map.of("password", "Wrong1234")).code());
        assertEquals("TWO_FACTOR_SETUP_NOT_STARTED", call("POST", "/api/users/me/2fa/confirm", session, Map.of("code", "123456")).code());
        Enabled enabled = enable(session);
        assertTrue(call("GET", "/api/users/me", session, null).body().path("twoFactorEnabled").asBoolean(),
                "this device stays logged in");

        Reply login = passwordStep(email, PASSWORD);
        assertEquals("CODE_REQUIRED", login.body().path("twoFactor").asText());
        assertTrue(login.body().path("token").isMissingNode());
        String challenge = login.body().path("challengeToken").asText();

        assertEquals("INVALID_TWO_FACTOR_CODE", codeStep(challenge, wrongCode(enabled.secret())).code());
        assertEquals("CODE_ALREADY_USED", codeStep(challenge, enabled.confirmCode()).code(),
                "the code used to confirm the setup can't be used again");
        forgetUsedCodes(email);
        assertEquals(200, codeStep(challenge, currentCode(enabled.secret())).status());

        // A recovery code works once, typed in any case and with or without the dash.
        String recovery = enabled.recoveryCodes().get(0);
        Reply withRecovery = codeStep(passwordStep(email, PASSWORD).body().path("challengeToken").asText(),
                recovery.toUpperCase().replace("-", " "));
        assertEquals(200, withRecovery.status(), withRecovery.body().toString());
        assertEquals("INVALID_TWO_FACTOR_CODE",
                codeStep(passwordStep(email, PASSWORD).body().path("challengeToken").asText(), recovery).code());

        assertEquals("INVALID_TWO_FACTOR_CODE", disable(session, wrongCode(enabled.secret())).code());
        assertEquals("WRONG_PASSWORD", call("POST", "/api/users/me/2fa/disable", session,
                Map.of("password", "Wrong1234", "code", enabled.recoveryCodes().get(1))).code());
        assertEquals(204, disable(session, enabled.recoveryCodes().get(1)).status());
        assertEquals("TWO_FACTOR_OFF", disable(session, enabled.recoveryCodes().get(2)).code());
        assertFalse(passwordStep(email, PASSWORD).body().path("token").asText().isBlank(), "the password is enough again");
    }

    @Test
    @DisplayName("wrong codes lock the account like wrong passwords, and logging in again doesn't reset the count")
    void wrongCodesLock() throws Exception {
        String session = newMember();
        String email = memberEmail(session);
        Enabled enabled = enable(session);

        String challenge = passwordStep(email, PASSWORD).body().path("challengeToken").asText();
        for (int i = 0; i < 3; i++) {
            assertEquals("INVALID_TWO_FACTOR_CODE", codeStep(challenge, wrongCode(enabled.secret())).code());
        }
        // Entering the right password again must not give 5 more guesses.
        challenge = passwordStep(email, PASSWORD).body().path("challengeToken").asText();
        assertEquals("INVALID_TWO_FACTOR_CODE", codeStep(challenge, wrongCode(enabled.secret())).code());
        Reply locked = codeStep(challenge, wrongCode(enabled.secret()));
        assertEquals("ACCOUNT_LOCKED", locked.code());
        assertTrue(locked.body().path("message").asText().startsWith("Too many wrong codes."));

        forgetUsedCodes(email);
        assertEquals("ACCOUNT_LOCKED", codeStep(challenge, currentCode(enabled.secret())).code(), "even the right code");
        Reply passwordWhileLocked = passwordStep(email, PASSWORD);
        assertEquals("ACCOUNT_LOCKED", passwordWhileLocked.code());
        assertTrue(passwordWhileLocked.body().path("message").asText().startsWith("Too many wrong attempts."));
    }

    @Test
    @DisplayName("a login challenge ends with the sessions, and can't be faked")
    void challengeExpires() throws Exception {
        String session = newMember();
        String email = memberEmail(session);
        Enabled enabled = enable(session);
        String challenge = passwordStep(email, PASSWORD).body().path("challengeToken").asText();

        assertEquals(204, call("POST", "/api/users/me/logout-all", session, null).status());
        forgetUsedCodes(email);
        assertEquals("LOGIN_EXPIRED", codeStep(challenge, currentCode(enabled.secret())).code());
        assertEquals("LOGIN_EXPIRED", codeStep("not-a-token", currentCode(enabled.secret())).code());
        assertEquals("LOGIN_EXPIRED", codeStep(newMember(), currentCode(enabled.secret())).code(),
                "an access token isn't a challenge");
        assertEquals("VALIDATION_FAILED", codeStep(challenge, "").code());
    }

    @Test
    @DisplayName("moving to a new phone needs the old app or a recovery code, and replaces the recovery codes")
    void moveToNewPhone() throws Exception {
        String session = newMember();
        String email = memberEmail(session);
        Enabled oldPhone = enable(session);

        assertEquals("TWO_FACTOR_CODE_REQUIRED",
                call("POST", "/api/users/me/2fa/setup", session, Map.of("password", PASSWORD)).code());
        Reply setup = call("POST", "/api/users/me/2fa/setup", session,
                Map.of("password", PASSWORD, "code", oldPhone.recoveryCodes().get(0)));
        assertEquals(200, setup.status(), setup.body().toString());
        String newSecret = setup.body().path("secret").asText();

        // Until the new app is confirmed, the old one keeps working.
        forgetUsedCodes(email);
        assertEquals(200, codeStep(passwordStep(email, PASSWORD).body().path("challengeToken").asText(),
                currentCode(oldPhone.secret())).status());

        Reply confirmed = call("POST", "/api/users/me/2fa/confirm", session, Map.of("code", currentCode(newSecret)));
        assertEquals(200, confirmed.status(), confirmed.body().toString());
        assertTrue(confirmed.body().path("token").isMissingNode(), "this session goes on; no new one");
        List<String> newCodes = texts(confirmed.body().path("recoveryCodes"));

        forgetUsedCodes(email);
        assertEquals("INVALID_TWO_FACTOR_CODE", codeStep(passwordStep(email, PASSWORD).body().path("challengeToken").asText(),
                currentCode(oldPhone.secret())).code(), "the old phone no longer works");
        assertEquals("INVALID_TWO_FACTOR_CODE", codeStep(passwordStep(email, PASSWORD).body().path("challengeToken").asText(),
                oldPhone.recoveryCodes().get(1)).code(), "neither do the old recovery codes");
        assertEquals(200, codeStep(passwordStep(email, PASSWORD).body().path("challengeToken").asText(),
                newCodes.get(0)).status());
        forgetUsedCodes(email);
        assertEquals(200, codeStep(passwordStep(email, PASSWORD).body().path("challengeToken").asText(),
                currentCode(newSecret)).status());
    }

    /** Turns two-factor authentication on through the Profile endpoints. */
    private static Enabled enable(String session) throws Exception {
        Reply setup = call("POST", "/api/users/me/2fa/setup", session, Map.of("password", PASSWORD));
        assertEquals(200, setup.status(), setup.body().toString());
        String secret = setup.body().path("secret").asText();
        assertEquals("INVALID_TWO_FACTOR_CODE", call("POST", "/api/users/me/2fa/confirm", session,
                Map.of("code", wrongCode(secret))).code());
        String code = currentCode(secret);
        Reply confirmed = call("POST", "/api/users/me/2fa/confirm", session, Map.of("code", code));
        assertEquals(200, confirmed.status(), confirmed.body().toString());
        List<String> codes = texts(confirmed.body().path("recoveryCodes"));
        assertEquals(8, codes.size());
        assertTrue(codes.get(0).matches("[a-z2-9]{5}-[a-z2-9]{5}"), codes.get(0));
        return new Enabled(secret, code, codes);
    }

    /** Back to an admin who has never set up an authenticator app. */
    private static void resetAdminTwoFactor() {
        jdbc().update("delete from recovery_codes where user_id = (select id from users where email = ?)", ADMIN_EMAIL);
        jdbc().update("update users set two_factor_secret = null, pending_two_factor_secret = null,"
                + " two_factor_last_step = null where email = ?", ADMIN_EMAIL);
    }

    private static Reply passwordStep(String email, String password) throws Exception {
        Reply reply = call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
        if (reply.status() == 200) {
            assertTrue(reply.body().has("token") || reply.body().has("challengeToken"), reply.body().toString());
        }
        return reply;
    }

    private static Reply codeStep(String challenge, String code) throws Exception {
        return call("POST", "/api/auth/login/2fa", null, Map.of("challengeToken", challenge, "code", code));
    }

    private static Reply confirmAtLogin(String challenge, String code) throws Exception {
        return call("POST", "/api/auth/login/2fa/confirm", null, Map.of("challengeToken", challenge, "code", code));
    }

    private static Reply disable(String session, String code) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("password", PASSWORD);
        body.put("code", code);
        return call("POST", "/api/users/me/2fa/disable", session, body);
    }

    /** A 6-digit code that is surely wrong: the right one with its last digit changed. */
    private static String wrongCode(String secret) {
        String right = currentCode(secret);
        char last = right.charAt(5);
        return right.substring(0, 5) + (last == '9' ? '0' : (char) (last + 1));
    }

    private static List<String> texts(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(item -> list.add(item.asText()));
        return list;
    }
}
