package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.AcceptInviteRequest;
import com.mycompany.gymbooking.dto.AuthResponse;
import com.mycompany.gymbooking.dto.LoginRequest;
import com.mycompany.gymbooking.dto.MessageResponse;
import com.mycompany.gymbooking.dto.ResendCodeRequest;
import com.mycompany.gymbooking.dto.SignUpRequest;
import com.mycompany.gymbooking.dto.VerifyEmailRequest;
import com.mycompany.gymbooking.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return authService.signUp(request);
    }

    @PostMapping("/verify")
    public AuthResponse verify(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/resend-code")
    public MessageResponse resendCode(@Valid @RequestBody ResendCodeRequest request) {
        return authService.resendCode(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/accept-invite")
    public AuthResponse acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        return authService.acceptInvite(request);
    }
}
