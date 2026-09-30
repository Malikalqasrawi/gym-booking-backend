package com.mycompany.gymbooking.dto;

import com.mycompany.gymbooking.model.Role;
import com.mycompany.gymbooking.model.User;

/** Returned instead of the entity so credentials and verification data are never exposed. */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        String title
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getDisplayTitle()
        );
    }
}
