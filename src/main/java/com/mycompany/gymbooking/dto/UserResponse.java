package com.mycompany.gymbooking.dto;

import com.mycompany.gymbooking.model.Role;
import com.mycompany.gymbooking.model.User;

/**
 * What we send BACK to the app about a user.
 *
 * Notice what is NOT here: passwordHash, verificationCode.
 * We never return the entity directly, so secrets can't leak by accident.
 */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        String title
) {

    /** Converts an entity (database object) into a response (JSON object). */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),          // polymorphism: Member/Trainer/Admin each answer differently
                user.getDisplayTitle()   // polymorphism again
        );
    }
}
