package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Google sign-in for members: new accounts, linking existing ones, and who may not use it. */
class GoogleSignInApiTest extends ApiTestBase {

    @Test
    @DisplayName("a new member signs up with Google, adds a phone number, and comes back to the same account")
    void newMemberSignsUp() throws Exception {
        String email = "google" + MEMBER_NUMBER.incrementAndGet() + "@gmail.com";
        Reply first = google(google.token("sub-" + email, email.toUpperCase()).name("Malik Qasrawi").sign());
        assertEquals(200, first.status(), first.body().toString());
        String session = first.body().path("token").asText();
        assertEquals("MEMBER", first.body().path("user").path("role").asText());
        assertEquals("Malik Qasrawi", first.body().path("user").path("fullName").asText());
        assertEquals(email, first.body().path("user").path("email").asText(), "stored in lower case like other emails");
        assertTrue(first.body().path("user").path("phone").isNull(), "Google doesn't share phone numbers");
        assertFalse(first.body().path("user").path("hasPassword").asBoolean());

        assertEquals("VALIDATION_FAILED", call("PUT", "/api/users/me/phone", session, Map.of("phone", "12ab")).code());
        Reply phone = call("PUT", "/api/users/me/phone", session, Map.of("phone", "0791234567"));
        assertEquals("+962791234567", phone.body().path("phone").asText(), "stored with the country code: " + phone.body());

        Reply again = google(google.idToken("sub-" + email, email));
        assertEquals(first.body().path("user").path("id").asLong(), again.body().path("user").path("id").asLong());
        assertEquals("+962791234567", again.body().path("user").path("phone").asText());
        assertEquals("INVALID_CREDENTIALS", call("POST", "/api/auth/login", null,
                Map.of("email", email, "password", "Secret1234")).code(), "no password until one is set with Forgot password");
    }

    @Test
    @DisplayName("a member who signed up with their Gmail address is linked, and the password keeps working")
    void linksGmailMember() throws Exception {
        String email = "linked" + MEMBER_NUMBER.incrementAndGet() + "@gmail.com";
        long memberId = signUpAndVerify(email);

        Reply viaGoogle = google(google.idToken("sub-" + email, email));
        assertEquals(200, viaGoogle.status(), viaGoogle.body().toString());
        assertEquals(memberId, viaGoogle.body().path("user").path("id").asLong());
        assertTrue(viaGoogle.body().path("user").path("hasPassword").asBoolean());
        assertEquals(200, call("POST", "/api/auth/login", null, Map.of("email", email, "password", "Secret1234")).status());
    }

    @Test
    @DisplayName("an existing account is linked only when Google owns the email address")
    void otherEmailProviders() throws Exception {
        String session = newMember();
        String email = memberEmail(session);   // @test.com: Google can't vouch for it

        assertEquals("ACCOUNT_EXISTS", google(google.idToken("sub-" + email, email)).code());
        Reply workspace = google(google.token("sub-" + email, email).hostedDomain("test.com").sign());
        assertEquals(200, workspace.status(), "a Google Workspace domain is owned by Google: " + workspace.body());
    }

    @Test
    @DisplayName("trainers and admins can't log in with Google")
    void membersOnly() throws Exception {
        Reply trainer = google(google.token("sub-sara", "sara.trainer@gym.com").hostedDomain("gym.com").sign());
        assertEquals("GOOGLE_MEMBERS_ONLY", trainer.code());
        assertEquals(403, trainer.status());
        assertEquals("GOOGLE_MEMBERS_ONLY", google(google.token("sub-admin", ADMIN_EMAIL).hostedDomain("gym.com").sign()).code());
    }

    @Test
    @DisplayName("someone who signed up with your Gmail address but never verified it loses that account")
    void unverifiedSquatter() throws Exception {
        String email = "squatted" + MEMBER_NUMBER.incrementAndGet() + "@gmail.com";
        assertEquals(201, call("POST", "/api/auth/signup", null, Map.of(
                "fullName", "Someone Else", "email", email, "phone", "0790000000", "password", "Secret1234")).status());

        Reply owner = google(google.idToken("sub-" + email, email));
        assertEquals(200, owner.status(), owner.body().toString());
        assertFalse(owner.body().path("user").path("hasPassword").asBoolean());
        assertEquals("INVALID_CREDENTIALS", call("POST", "/api/auth/login", null,
                Map.of("email", email, "password", "Secret1234")).code(), "the other person's password no longer works");
    }

    @Test
    @DisplayName("invalid Google tokens and unverified Google emails are refused")
    void invalidTokens() throws Exception {
        String email = "refused" + MEMBER_NUMBER.incrementAndGet() + "@gmail.com";
        assertEquals("INVALID_GOOGLE_TOKEN", google(google.token("s1", email).audience("another-app").sign()).code());
        assertEquals(401, google(google.token("s1", email).signedWithUnknownKey().sign()).status());
        assertEquals("GOOGLE_EMAIL_NOT_VERIFIED", google(google.token("s1", "someone@outlook.com").emailVerified(false).sign()).code());
        assertEquals("VALIDATION_FAILED", google("").code());
    }

    @Test
    @DisplayName("with two-factor authentication on, Google is only the first step")
    void twoFactorStillApplies() throws Exception {
        String email = "twostep" + MEMBER_NUMBER.incrementAndGet() + "@gmail.com";
        signUpAndVerify(email);
        String session = call("POST", "/api/auth/login", null, Map.of("email", email, "password", "Secret1234"))
                .body().path("token").asText();
        String secret = call("POST", "/api/users/me/2fa/setup", session, Map.of("password", "Secret1234"))
                .body().path("secret").asText();
        assertEquals(200, call("POST", "/api/users/me/2fa/confirm", session, Map.of("code", currentCode(secret))).status());

        Reply viaGoogle = google(google.idToken("sub-" + email, email));
        assertEquals("CODE_REQUIRED", viaGoogle.body().path("twoFactor").asText());
        assertTrue(viaGoogle.body().path("token").isMissingNode());
        forgetUsedCodes(email);
        Reply loggedIn = call("POST", "/api/auth/login/2fa", null, Map.of(
                "challengeToken", viaGoogle.body().path("challengeToken").asText(), "code", currentCode(secret)));
        assertEquals(200, loggedIn.status(), loggedIn.body().toString());
    }

    private static Reply google(String idToken) throws Exception {
        return call("POST", "/api/auth/google", null, Map.of("idToken", idToken));
    }

    /** Signs up with a password and verifies the email; returns the member's ID. */
    private static long signUpAndVerify(String email) throws Exception {
        Reply signUp = call("POST", "/api/auth/signup", null, Map.of(
                "fullName", "Test Member", "email", email, "phone", "0790000000", "password", "Secret1234"));
        assertEquals(201, signUp.status(), signUp.body().toString());
        Reply verified = call("POST", "/api/auth/verify", null,
                Map.of("email", email, "code", mailbox.latestVerificationCode(email)));
        assertEquals(200, verified.status(), verified.body().toString());
        return verified.body().path("user").path("id").asLong();
    }
}
