package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Either a session (the same fields as {@link AuthResponse}), or, for two-factor accounts, the next
 * step and a {@code challengeToken} to send with it. Fields that don't apply are left out of the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        @JsonUnwrapped AuthResponse session,
        TwoFactorStep twoFactor,
        String challengeToken
) {

    public static LoginResponse loggedIn(AuthResponse session) {
        return new LoginResponse(session, null, null);
    }

    public static LoginResponse nextStep(TwoFactorStep step, String challengeToken) {
        return new LoginResponse(null, step, challengeToken);
    }
}
