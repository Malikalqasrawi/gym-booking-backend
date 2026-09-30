package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.UserResponse;
import com.mycompany.gymbooking.security.SecurityUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal SecurityUser currentUser) {
        return UserResponse.from(currentUser.getUser());
    }
}
