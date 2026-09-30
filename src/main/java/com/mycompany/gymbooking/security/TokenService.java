package com.mycompany.gymbooking.security;

import com.mycompany.gymbooking.model.User;
import java.util.Optional;

/** Issues and reads login tokens. */
public interface TokenService {

    String generateToken(User user);

    /** Returns the email inside the token, or empty if the token is invalid or expired. */
    Optional<String> readEmail(String token);
}
