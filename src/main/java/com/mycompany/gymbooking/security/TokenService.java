package com.mycompany.gymbooking.security;

import com.mycompany.gymbooking.model.User;
import java.util.Optional;

/** Issues and reads short-lived access tokens, and the tokens for the second step of a two-factor login. */
public interface TokenService {

    /** What a valid access token says: whose it is, and the user's token version when it was issued. */
    record AccessToken(String email, int version) {
    }

    /** What a valid login challenge says: who entered the right password, and their token version then. */
    record LoginChallenge(String email, int version) {
    }

    String generateToken(User user);

    /** Returns the token's contents, or empty if it is invalid or expired. */
    Optional<AccessToken> read(String token);

    /**
     * A token proving the password was right, for the code step of a two-factor login. It can't be
     * used as an access token.
     */
    String generateLoginChallenge(User user);

    /** Returns the challenge's contents, or empty if it is invalid, expired or not a challenge. */
    Optional<LoginChallenge> readLoginChallenge(String token);
}
