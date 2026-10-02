package com.mycompany.gymbooking.security;

import com.mycompany.gymbooking.exception.ServiceUnavailableException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import java.security.Key;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Checks Google ID tokens, the proof of a Google sign-in that the app sends to the backend. A token
 * is accepted only if Google signed it with one of its published keys, it was issued for this app
 * (its audience is our client ID) and it hasn't expired.
 */
@Component
public class GoogleIdTokenVerifier {

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    /** Google changes its signing keys every few weeks, so the key set is fetched again after this. */
    private static final Duration KEYS_MAX_AGE = Duration.ofHours(1);
    /** A token with an unknown key fetches the key set again, but at most this often. */
    private static final Duration MIN_TIME_BETWEEN_FETCHES = Duration.ofMinutes(1);

    /** What a valid ID token says about the Google account. */
    public record GoogleAccount(String subject, String email, boolean emailVerified, String hostedDomain, String name) {

        /**
         * Whether Google, and not only the user, controls this email address: Gmail addresses and
         * verified addresses of Google Workspace domains. Only then may an existing account with the
         * same email be linked.
         */
        public boolean googleOwnsEmail() {
            return email.toLowerCase(Locale.ROOT).endsWith("@gmail.com") || (emailVerified && hostedDomain != null);
        }
    }

    private final RestClient http;
    private final Clock clock;
    private final String clientId;
    private final String keysUri;
    private Map<String, Key> keys = Map.of();
    private Instant fetchedAt = Instant.EPOCH;

    public GoogleIdTokenVerifier(RestClient.Builder builder,
                                 Clock clock,
                                 @Value("${app.auth.google.client-id:}") String clientId,
                                 @Value("${app.auth.google.jwks-uri}") String keysUri) {
        this.http = builder.build();
        this.clock = clock;
        this.clientId = clientId.trim();
        this.keysUri = keysUri;
    }

    /** False when no client ID is configured, so Google sign-in is off. */
    public boolean isEnabled() {
        return !clientId.isEmpty();
    }

    /** The Google account the token belongs to, or empty if the token isn't valid for this app. */
    public Optional<GoogleAccount> verify(String idToken) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(JwsHeader header) {
                            return keyFor(header.getKeyId());
                        }
                    })
                    .clock(() -> Date.from(clock.instant()))
                    .clockSkewSeconds(60)   // the phone's and the server's clocks may differ a little
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }

        String email = claims.get("email") instanceof String text ? text : null;
        if (!ISSUERS.contains(claims.getIssuer())
                || claims.getAudience() == null || !claims.getAudience().contains(clientId)
                || claims.getSubject() == null || email == null) {
            return Optional.empty();
        }
        Object verified = claims.get("email_verified");
        return Optional.of(new GoogleAccount(
                claims.getSubject(),
                email,
                Boolean.TRUE.equals(verified) || "true".equals(verified),
                claims.get("hd") instanceof String domain ? domain : null,
                claims.get("name") instanceof String name ? name : null));
    }

    private synchronized Key keyFor(String keyId) {
        Instant now = clock.instant();
        boolean outdated = now.isAfter(fetchedAt.plus(KEYS_MAX_AGE));
        boolean unknown = keyId == null || !keys.containsKey(keyId);
        if (outdated || (unknown && now.isAfter(fetchedAt.plus(MIN_TIME_BETWEEN_FETCHES)))) {
            keys = fetchKeys();
            fetchedAt = now;
        }
        Key key = keyId == null ? null : keys.get(keyId);
        if (key == null) {
            throw new JwtException("The token was not signed with one of Google's keys");
        }
        return key;
    }

    private Map<String, Key> fetchKeys() {
        try {
            String body = http.get().uri(keysUri).retrieve().body(String.class);
            JwkSet set = Jwks.setParser().build().parse(body);
            Map<String, Key> found = new HashMap<>();
            for (Jwk<?> jwk : set.getKeys()) {
                if (jwk.getId() != null && jwk.toKey() instanceof PublicKey publicKey) {
                    found.put(jwk.getId(), publicKey);
                }
            }
            return Map.copyOf(found);
        } catch (RestClientException | IllegalArgumentException | JwtException e) {
            throw new ServiceUnavailableException("GOOGLE_UNAVAILABLE",
                    "Google can't be reached right now. Please try again in a moment.");
        }
    }
}
