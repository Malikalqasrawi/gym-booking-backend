package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Emails sent when a password, two-factor authentication or Google sign-in changes. */
class SecurityAlertsApiTest extends ApiTestBase {

    @Test
    @DisplayName("changing or resetting the password sends an alert that says what to do if it wasn't you")
    void passwordAlerts() throws Exception {
        String session = newMember();
        String email = memberEmail(session);

        Reply changed = call("POST", "/api/users/me/password", session,
                Map.of("currentPassword", "Secret1234", "newPassword", "NewPass123"));
        assertEquals(200, changed.status(), changed.body().toString());
        assertEquals(1, mailbox.count(email, "Your Gym Booking password was changed"));
        String alert = mailbox.latestBody(email, "Your Gym Booking password was changed");
        assertTrue(alert.contains("  Account:   " + email), alert);
        assertTrue(alert.contains("If it wasn't, someone knows your password."), alert);

        resetPassword(email, "Another123");
        assertEquals(1, mailbox.count(email, "Your Gym Booking password was reset"));
        assertTrue(mailbox.latestBody(email, "Your Gym Booking password was reset").contains("someone can read your email"));
    }

    @Test
    @DisplayName("turning two-factor authentication on, moving it to a new phone and turning it off each send an alert")
    void twoFactorAlerts() throws Exception {
        String session = newMember();
        String email = memberEmail(session);

        String secret = call("POST", "/api/users/me/2fa/setup", session, Map.of("password", "Secret1234"))
                .body().path("secret").asText();
        Reply on = call("POST", "/api/users/me/2fa/confirm", session, Map.of("code", currentCode(secret)));
        assertEquals(200, on.status(), on.body().toString());
        assertEquals(1, mailbox.count(email, "Two-factor authentication is on"));

        String recoveryCode = on.body().path("recoveryCodes").get(0).asText();
        String newSecret = call("POST", "/api/users/me/2fa/setup", session, Map.of("password", "Secret1234", "code", recoveryCode))
                .body().path("secret").asText();
        forgetUsedCodes(email);
        assertEquals(200, call("POST", "/api/users/me/2fa/confirm", session, Map.of("code", currentCode(newSecret))).status());
        assertEquals(1, mailbox.count(email, "Two-factor authentication moved to a new phone"));
        assertEquals(1, mailbox.count(email, "Two-factor authentication is on"), "moving isn't reported as turning it on");

        forgetUsedCodes(email);
        Reply off = call("POST", "/api/users/me/2fa/disable", session, Map.of("password", "Secret1234", "code", currentCode(newSecret)));
        assertTrue(off.status() < 300, off.body().toString());
        assertEquals(1, mailbox.count(email, "Two-factor authentication was turned off"));
    }

    @Test
    @DisplayName("adding Google sign-in to an existing account sends an alert; a new Google account gets none")
    void googleAlerts() throws Exception {
        String existing = "alert" + MEMBER_NUMBER.incrementAndGet() + "@gmail.com";
        assertEquals(201, call("POST", "/api/auth/signup", null, Map.of(
                "fullName", "Test Member", "email", existing, "phone", "0790000000", "password", "Secret1234")).status());
        assertEquals(200, call("POST", "/api/auth/verify", null,
                Map.of("email", existing, "code", mailbox.latestVerificationCode(existing), "password", "Secret1234")).status());

        assertEquals(200, googleLogin(existing).status());
        assertEquals(200, googleLogin(existing).status());
        assertEquals(1, mailbox.count(existing, "Google sign-in was added"), "only when it's added");

        String fresh = "fresh" + MEMBER_NUMBER.incrementAndGet() + "@gmail.com";
        assertEquals(200, googleLogin(fresh).status());
        assertEquals(0, mailbox.count(fresh, "Google sign-in was added"), "a new account has nothing to protect yet");

        resetPassword(fresh, "FirstPass123");
        assertEquals(1, mailbox.count(fresh, "A password was added to your Gym Booking account"));
        assertEquals(0, mailbox.count(fresh, "Your Gym Booking password was reset"));
    }

    private static Reply googleLogin(String email) throws Exception {
        return call("POST", "/api/auth/google", null, Map.of("idToken", google.idToken("sub-" + email, email)));
    }

    private static void resetPassword(String email, String newPassword) throws Exception {
        assertEquals(200, call("POST", "/api/auth/forgot-password", null, Map.of("email", email)).status());
        Matcher code = Pattern.compile("is: (\\d{6})").matcher(mailbox.latestBody(email, "Reset your Gym Booking password"));
        assertTrue(code.find());
        Reply reset = call("POST", "/api/auth/reset-password", null,
                Map.of("email", email, "code", code.group(1), "password", newPassword));
        assertEquals(200, reset.status(), reset.body().toString());
    }
}
