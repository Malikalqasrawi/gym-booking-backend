package com.mycompany.gymbooking.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.gymbooking.exception.ServiceUnavailableException;
import com.mycompany.gymbooking.security.GoogleIdTokenVerifier.GoogleAccount;
import com.mycompany.gymbooking.support.FakeGoogle;
import com.mycompany.gymbooking.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Google ID tokens: signature, audience, issuer, expiry, and fetching Google's keys. */
class GoogleIdTokenVerifierTest {

    private FakeGoogle google;
    private MutableClock clock;
    private GoogleIdTokenVerifier verifier;

    @BeforeEach
    void start() throws Exception {
        google = FakeGoogle.start();
        clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
        verifier = new GoogleIdTokenVerifier(RestClient.builder(), clock, FakeGoogle.CLIENT_ID, google.keysUrl());
    }

    @AfterEach
    void stop() {
        google.close();
    }

    @Test
    @DisplayName("a token Google signed for this app is accepted")
    void validToken() {
        GoogleAccount account = verifier.verify(google.token("sub-1", "malik@gmail.com").name("Malik Q").sign()).orElseThrow();
        assertEquals("sub-1", account.subject());
        assertEquals("malik@gmail.com", account.email());
        assertEquals("Malik Q", account.name());
        assertTrue(account.emailVerified());
    }

    @Test
    @DisplayName("tokens for another app, from another issuer, expired or forged are refused")
    void invalidTokens() {
        assertTrue(verifier.verify(google.token("s", "a@gmail.com").audience("other-app.apps.googleusercontent.com").sign()).isEmpty());
        assertTrue(verifier.verify(google.token("s", "a@gmail.com").issuer("https://evil.example.com").sign()).isEmpty());
        assertTrue(verifier.verify(google.token("s", "a@gmail.com").signedWithUnknownKey().sign()).isEmpty());
        assertTrue(verifier.verify("not.a.token").isEmpty());

        String token = google.idToken("s", "a@gmail.com");
        clock.advance(Duration.ofHours(2));
        assertTrue(verifier.verify(token).isEmpty(), "expired");
    }

    @Test
    @DisplayName("Google's keys are downloaded once, and again after Google changes them")
    void keyRotation() {
        verifier.verify(google.idToken("s", "a@gmail.com")).orElseThrow();
        verifier.verify(google.idToken("s", "a@gmail.com")).orElseThrow();
        assertEquals(1, google.keyRequests());

        google.rotateKey();
        clock.advance(Duration.ofMinutes(2));
        verifier.verify(google.idToken("s", "a@gmail.com")).orElseThrow();
        assertEquals(2, google.keyRequests(), "an unknown key ID fetches the keys again");

        google.rotateKey();
        for (int i = 0; i < 5; i++) {
            assertTrue(verifier.verify(google.idToken("s", "a@gmail.com")).isEmpty());
        }
        assertEquals(2, google.keyRequests(), "at most one download a minute, so made-up key IDs can't flood Google");
    }

    @Test
    @DisplayName("Google owns Gmail addresses and verified Workspace addresses, not other emails")
    void googleOwnsEmail() {
        assertTrue(new GoogleAccount("s", "Malik@Gmail.com", true, null, null).googleOwnsEmail());
        assertTrue(new GoogleAccount("s", "malik@asu.edu.jo", true, "asu.edu.jo", null).googleOwnsEmail());
        assertFalse(new GoogleAccount("s", "malik@outlook.com", true, null, null).googleOwnsEmail());
    }

    @Test
    @DisplayName("Google being unreachable is reported as unavailable, not as a bad token")
    void googleDown() {
        google.close();
        assertThrows(ServiceUnavailableException.class, () -> verifier.verify(google.idToken("s", "a@gmail.com")));
    }
}
