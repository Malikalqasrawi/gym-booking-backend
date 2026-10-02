package com.mycompany.gymbooking.security;

import com.mycompany.gymbooking.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService implements TokenService {

    private static final String VERSION_CLAIM = "ver";
    /** Set only on login challenges, so they can't be used as access tokens and the other way round. */
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String LOGIN_CHALLENGE = "2fa-login";
    /** Long enough to set up an authenticator app during an admin's first login. */
    private static final Duration CHALLENGE_VALIDITY = Duration.ofMinutes(10);

    private final SecretKey signingKey;
    private final Duration validity;

    public JwtTokenService(@Value("${app.jwt.secret}") String base64Secret,
                           @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.validity = Duration.ofMinutes(expirationMinutes);
    }

    @Override
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim(VERSION_CLAIM, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public Optional<AccessToken> read(String token) {
        return parse(token)
                .filter(claims -> claims.get(PURPOSE_CLAIM) == null)
                .map(claims -> new AccessToken(claims.getSubject(), claims.get(VERSION_CLAIM, Integer.class)));
    }

    @Override
    public String generateLoginChallenge(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(PURPOSE_CLAIM, LOGIN_CHALLENGE)
                .claim(VERSION_CLAIM, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(CHALLENGE_VALIDITY)))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public Optional<LoginChallenge> readLoginChallenge(String token) {
        return parse(token)
                .filter(claims -> LOGIN_CHALLENGE.equals(claims.get(PURPOSE_CLAIM)))
                .map(claims -> new LoginChallenge(claims.getSubject(), claims.get(VERSION_CLAIM, Integer.class)));
    }

    /** The claims of a correctly signed, unexpired token that has a subject and a version. */
    private Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (claims.getSubject() == null || claims.get(VERSION_CLAIM, Integer.class) == null) {
                return Optional.empty();   // e.g. issued before token versions existed
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
