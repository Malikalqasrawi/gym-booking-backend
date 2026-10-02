package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/**
 * Sent once when two-factor login is turned on: one-time codes for logging in without the phone.
 * Only their hashes are stored, so they can't be shown again. After an admin's setup during login,
 * the new session's fields are included too.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecoveryCodesResponse(
        List<String> recoveryCodes,
        @JsonUnwrapped AuthResponse session
) {
}
