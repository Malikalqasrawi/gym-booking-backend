package com.mycompany.gymbooking.security;

import com.mycompany.gymbooking.model.User;
import java.util.Optional;

/**
 * Contract for creating and reading login tokens.
 * Today it's implemented with JWT (JwtTokenService); the rest of the code doesn't need to know that.
 */
public interface TokenService {

    /** Creates a signed token for this user. */
    String generateToken(User user);

    /** Returns the email inside the token, or empty if the token is invalid or expired. */
    Optional<String> readEmail(String token);
}
