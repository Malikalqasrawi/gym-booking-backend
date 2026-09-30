package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.UserResponse;
import com.mycompany.gymbooking.security.SecurityUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints about the logged-in user.
 *
 *   GET /api/users/me   (needs header  Authorization: Bearer <token>)
 *
 * @AuthenticationPrincipal gives us the user that JwtAuthenticationFilter found from the token.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal SecurityUser currentUser) {
        return UserResponse.from(currentUser.getUser());
    }
}
