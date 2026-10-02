package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.AcceptInviteRequest;
import com.mycompany.gymbooking.dto.AuthResponse;
import com.mycompany.gymbooking.dto.ForgotPasswordRequest;
import com.mycompany.gymbooking.dto.LoginRequest;
import com.mycompany.gymbooking.dto.LoginResponse;
import com.mycompany.gymbooking.dto.MessageResponse;
import com.mycompany.gymbooking.dto.RecoveryCodesResponse;
import com.mycompany.gymbooking.dto.RefreshRequest;
import com.mycompany.gymbooking.dto.ResendCodeRequest;
import com.mycompany.gymbooking.dto.ResetPasswordRequest;
import com.mycompany.gymbooking.dto.SignUpRequest;
import com.mycompany.gymbooking.dto.TwoFactorChallengeRequest;
import com.mycompany.gymbooking.dto.TwoFactorLoginRequest;
import com.mycompany.gymbooking.dto.TwoFactorSetupResponse;
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
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/login/2fa")
    public AuthResponse loginWithCode(@Valid @RequestBody TwoFactorLoginRequest request) {
        return authService.loginWithCode(request);
    }

    /** For an admin who hasn't set up an authenticator app yet. */
    @PostMapping("/login/2fa/setup")
    public TwoFactorSetupResponse startLoginSetup(@Valid @RequestBody TwoFactorChallengeRequest request) {
        return authService.startLoginSetup(request);
    }

    @PostMapping("/login/2fa/confirm")
    public RecoveryCodesResponse confirmLoginSetup(@Valid @RequestBody TwoFactorLoginRequest request) {
        return authService.confirmLoginSetup(request);
    }

    @PostMapping("/accept-invite")
    public AuthResponse acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        return authService.acceptInvite(request);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }
}
