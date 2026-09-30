package com.mycompany.gymbooking.security;

import com.mycompany.gymbooking.model.User;
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

/**
 * JWT = JSON Web Token. It looks like  xxxxx.yyyyy.zzzzz
 *   xxxxx = header   (which algorithm)
 *   yyyyy = payload  (email, role, expiry time)  ← anyone can READ this, so no secrets inside
 *   zzzzz = signature (made with our secret key) ← nobody can CHANGE the payload without breaking this
 *
 * So when the app sends the token back, we can trust "this is really user X" without a database lookup of sessions.
 */
@Service
public class JwtTokenService implements TokenService {

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
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public Optional<String> readEmail(String token) {
        try {
            String email = Jwts.parser()
                    .verifyWith(signingKey)           // checks the signature
                    .build()
                    .parseSignedClaims(token)         // also checks the expiry time
                    .getPayload()
                    .getSubject();
            return Optional.ofNullable(email);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();                  // fake, changed or expired token
        }
    }
}
