package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.gymbooking.service.UnverifiedAccountCleanup;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Sign-ups for someone else's email, emails anyone can trigger, and how codes are stored. */
class AccountSecurityApiTest extends ApiTestBase {

    @Test
    @DisplayName("signing up first with someone else's email doesn't let you keep it: their sign-up replaces yours")
    void squatterLosesUnverifiedSignUp() throws Exception {
        String email = "victim" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        assertEquals(201, signUp(email, "Someone Else", "Attacker123").status());
        String attackersCode = mailbox.latestVerificationCode(email);

        assertEquals("RESEND_TOO_SOON", signUp(email, "Real Owner", "Owner12345").code(), "one code a minute, as always");
        allowNewCode(email);
        Reply owner = signUp(email, "Real Owner", "Owner12345");
        assertEquals(201, owner.status(), owner.body().toString());
        String ownersCode = mailbox.latestVerificationCode(email);
        assertNotEquals(attackersCode, ownersCode);

        assertEquals("INVALID_CODE", verify(email, attackersCode, "Attacker123").code(), "the old code was replaced");
        Reply wrongPassword = verify(email, ownersCode, "Attacker123");
        assertEquals("SIGN_UP_REPLACED", wrongPassword.code(), "the code only verifies the sign-up it was sent for");

        Reply verified = verify(email, ownersCode, "Owner12345");
        assertEquals(200, verified.status(), verified.body().toString());
        assertEquals("Real Owner", verified.body().path("user").path("fullName").asText());
        assertEquals("INVALID_CREDENTIALS", loginReply(email, "Attacker123").code());
        assertEquals(200, loginReply(email, "Owner12345").status());
    }

    @Test
    @DisplayName("signing up with an email that has an account gets the usual answer, and the owner is told")
    void existingAccountIsNotRevealed() throws Exception {
        String email = memberEmail(newMember());
        Reply again = signUp(email, "Someone Else", "Attacker123");
        assertEquals(201, again.status(), again.body().toString());
        assertEquals("Check your email: we sent a message to " + email + ".", again.body().path("message").asText());
        assertEquals(1, mailbox.count(email, "Someone tried to sign up with your email"));
        assertEquals(200, loginReply(email, "Secret1234").status(), "the account didn't change");
        assertEquals("INVALID_CREDENTIALS", loginReply(email, "Attacker123").code());
    }

    @Test
    @DisplayName("sign-ups that are never verified are deleted after 48 hours")
    void unverifiedSignUpsExpire() throws Exception {
        String old = "stale" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        String recent = "recent" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        signUp(old, "Test Member", "Secret1234");
        signUp(recent, "Test Member", "Secret1234");
        String verified = memberEmail(newMember());
        jdbc().update("update users set verification_code_sent_at = ? where email in (?, ?)",
                LocalDateTime.now(AMMAN).minusHours(49), old, verified);

        backend.getBean(UnverifiedAccountCleanup.class).deleteOld();
        assertEquals(0, accounts(old));
        assertEquals(1, accounts(recent), "still within 48 hours");
        assertEquals(1, accounts(verified), "verified accounts are never deleted");
        assertEquals(201, signUp(old, "Real Owner", "Owner12345").status(), "the email is free again");
    }

    @Test
    @DisplayName("an address gets at most 5 emailed codes a day")
    void dailyCodeLimit() throws Exception {
        String email = memberEmail(newMember());   // 1 code: the sign-up
        for (int i = 0; i < 6; i++) {
            allowNewCode(email);
            assertEquals(200, call("POST", "/api/auth/forgot-password", null, Map.of("email", email)).status(),
                    "same answer either way, so accounts can't be found");
        }
        assertEquals(4, mailbox.count(email, "Reset your Gym Booking password"), "1 sign-up code + 4 reset codes");

        String unverified = "codes" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        signUp(unverified, "Test Member", "Secret1234");
        for (int i = 1; i < 5; i++) {
            allowNewCode(unverified);
            assertEquals(200, call("POST", "/api/auth/resend-code", null, Map.of("email", unverified)).status());
        }
        allowNewCode(unverified);
        Reply limit = call("POST", "/api/auth/resend-code", null, Map.of("email", unverified));
        assertEquals("TOO_MANY_CODES", limit.code());
        assertEquals(429, limit.status());
        assertEquals(5, mailbox.count(unverified, "Your Gym Booking verification code"));
    }

    @Test
    @DisplayName("emailed codes are stored as a hash, and the email doesn't repeat the name someone typed")
    void codesAreHashed() throws Exception {
        String email = "hashed" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        signUp(email, "Test Member", "Secret1234");
        String code = mailbox.latestVerificationCode(email);
        String stored = jdbc().queryForObject("select verification_code from users where email = ?", String.class, email);
        assertEquals(64, stored.length());
        assertNotEquals(code, stored);
        assertTrue(mailbox.latestBody(email, "Your Gym Booking verification code").startsWith("Hello,"));
        assertEquals(200, verify(email, code, "Secret1234").status());
    }

    @Test
    @DisplayName("names can only have letters, so emails can't carry links or messages")
    void namesAreLettersOnly() throws Exception {
        Reply link = signUp("name" + MEMBER_NUMBER.incrementAndGet() + "@test.com", "Visit http://evil.example", "Secret1234");
        assertEquals("VALIDATION_FAILED", link.code());
        assertEquals("Use letters only in your name", link.body().path("fieldErrors").path("fullName").asText());
        assertEquals(201, signUp("name" + MEMBER_NUMBER.incrementAndGet() + "@test.com", "مالك القصراوي", "Secret1234").status());
        assertEquals(201, signUp("name" + MEMBER_NUMBER.incrementAndGet() + "@test.com", "Anne-Marie O'Neil Jr.", "Secret1234").status());
    }

    private static Reply signUp(String email, String name, String password) throws Exception {
        return call("POST", "/api/auth/signup", null, Map.of(
                "fullName", name, "email", email, "phone", "0790000000", "password", password));
    }

    private static Reply verify(String email, String code, String password) throws Exception {
        return call("POST", "/api/auth/verify", null, Map.of("email", email, "code", code, "password", password));
    }

    private static Reply loginReply(String email, String password) throws Exception {
        return call("POST", "/api/auth/login", null, Map.of("email", email, "password", password));
    }

    /** Moves the last code back, so the one-a-minute rule allows the next one. */
    private static void allowNewCode(String email) {
        jdbc().update("update users set verification_code_sent_at = ? where email = ?",
                LocalDateTime.now(AMMAN).minusMinutes(2), email);
    }

    private static int accounts(String email) {
        return jdbc().queryForObject("select count(*) from users where email = ?", Integer.class, email);
    }
}
