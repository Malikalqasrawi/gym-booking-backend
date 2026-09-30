package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AuthResponse;
import com.mycompany.gymbooking.dto.LoginRequest;
import com.mycompany.gymbooking.dto.MessageResponse;
import com.mycompany.gymbooking.dto.ResendCodeRequest;
import com.mycompany.gymbooking.dto.SignUpRequest;
import com.mycompany.gymbooking.dto.VerifyEmailRequest;

/**
 * WHAT the authentication feature can do (not HOW).
 * AuthController depends on this interface, not on the implementation class.
 */
public interface AuthService {

    /** Creates a new (unverified) member and sends them a verification code. */
    MessageResponse signUp(SignUpRequest request);

    /** Checks the code; if correct, marks the email verified and logs the user in. */
    AuthResponse verifyEmail(VerifyEmailRequest request);

    /** Sends a fresh code (old one stops working). */
    MessageResponse resendCode(ResendCodeRequest request);

    /** Checks email + password and returns a token. */
    AuthResponse login(LoginRequest request);
}
