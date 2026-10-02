package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AcceptInviteRequest;
import com.mycompany.gymbooking.dto.AuthResponse;
import com.mycompany.gymbooking.dto.ForgotPasswordRequest;
import com.mycompany.gymbooking.dto.LoginRequest;
import com.mycompany.gymbooking.dto.MessageResponse;
import com.mycompany.gymbooking.dto.ResendCodeRequest;
import com.mycompany.gymbooking.dto.ResetPasswordRequest;
import com.mycompany.gymbooking.dto.SignUpRequest;
import com.mycompany.gymbooking.dto.VerifyEmailRequest;

/** Member sign-up, email verification, trainer invites, login and password resets. */
public interface AuthService {

    /** Creates an unverified member and sends a verification code. */
    MessageResponse signUp(SignUpRequest request);

    /** Verifies the email with the given code and logs the user in. */
    AuthResponse verifyEmail(VerifyEmailRequest request);

    /** Sends a new code and invalidates the previous one. */
    MessageResponse resendCode(ResendCodeRequest request);

    AuthResponse login(LoginRequest request);

    /** Sets an invited trainer's password with the emailed invite code and logs them in. */
    AuthResponse acceptInvite(AcceptInviteRequest request);

    /** Emails a reset code to a verified, active account. Answers the same whether or not the email exists. */
    MessageResponse forgotPassword(ForgotPasswordRequest request);

    /** Sets a new password with the emailed reset code. The user then logs in with it. */
    MessageResponse resetPassword(ResetPasswordRequest request);
}
