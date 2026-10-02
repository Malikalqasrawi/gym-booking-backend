package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AcceptInviteRequest;
import com.mycompany.gymbooking.dto.AuthResponse;
import com.mycompany.gymbooking.dto.ChangePasswordRequest;
import com.mycompany.gymbooking.dto.ForgotPasswordRequest;
import com.mycompany.gymbooking.dto.GoogleLoginRequest;
import com.mycompany.gymbooking.dto.LoginRequest;
import com.mycompany.gymbooking.dto.LoginResponse;
import com.mycompany.gymbooking.dto.MessageResponse;
import com.mycompany.gymbooking.dto.RecoveryCodesResponse;
import com.mycompany.gymbooking.dto.RefreshRequest;
import com.mycompany.gymbooking.dto.ResendCodeRequest;
import com.mycompany.gymbooking.dto.ResetPasswordRequest;
import com.mycompany.gymbooking.dto.SignUpRequest;
import com.mycompany.gymbooking.dto.TwoFactorChallengeRequest;
import com.mycompany.gymbooking.dto.TwoFactorCodeRequest;
import com.mycompany.gymbooking.dto.TwoFactorDisableRequest;
import com.mycompany.gymbooking.dto.TwoFactorLoginRequest;
import com.mycompany.gymbooking.dto.TwoFactorSetupRequest;
import com.mycompany.gymbooking.dto.TwoFactorSetupResponse;
import com.mycompany.gymbooking.dto.VerifyEmailRequest;

/** Member sign-up, email verification, trainer invites, login, two-factor login, sessions and passwords. */
public interface AuthService {

    /** Creates an unverified member and sends a verification code. */
    MessageResponse signUp(SignUpRequest request);

    /** Verifies the email with the given code and logs the user in. */
    AuthResponse verifyEmail(VerifyEmailRequest request);

    /** Sends a new code and invalidates the previous one. */
    MessageResponse resendCode(ResendCodeRequest request);

    /**
     * Checks the email and password. Accounts with two-factor authentication get the next step and a
     * challenge token instead of a session.
     */
    LoginResponse login(LoginRequest request);

    /**
     * Logs a member in with a Google ID token. The first time, it links the member account with
     * the same email or creates one. Two-factor authentication still applies.
     */
    LoginResponse loginWithGoogle(GoogleLoginRequest request);

    /** Second login step: a code from the authenticator app, or a recovery code. */
    AuthResponse loginWithCode(TwoFactorLoginRequest request);

    /** An admin's first login: a new secret for their authenticator app. */
    TwoFactorSetupResponse startLoginSetup(TwoFactorChallengeRequest request);

    /** Finishes an admin's first login with a code from the new app: recovery codes and a session. */
    RecoveryCodesResponse confirmLoginSetup(TwoFactorLoginRequest request);

    /** Sets an invited trainer's password with the emailed invite code and logs them in. */
    AuthResponse acceptInvite(AcceptInviteRequest request);

    /** Emails a reset code to a verified, active account. Answers the same whether or not the email exists. */
    MessageResponse forgotPassword(ForgotPasswordRequest request);

    /** Sets a new password with the emailed reset code and ends all sessions. The user then logs in with it. */
    MessageResponse resetPassword(ResetPasswordRequest request);

    /** Trades a refresh token for a new access and refresh token. */
    AuthResponse refresh(RefreshRequest request);

    /** Ends the session the refresh token belongs to. */
    void logout(RefreshRequest request);

    /** Changes the password, ends the other sessions, and returns a new session for this device. */
    AuthResponse changePassword(Long userId, ChangePasswordRequest request);

    /** Ends every session of the user, on all devices. */
    void logoutEverywhere(Long userId);

    /** Starts setting up an authenticator app, or moving it to a new phone. */
    TwoFactorSetupResponse startTwoFactorSetup(Long userId, TwoFactorSetupRequest request);

    /** Turns two-factor authentication on with a code from the new app, and returns recovery codes. */
    RecoveryCodesResponse confirmTwoFactorSetup(Long userId, TwoFactorCodeRequest request);

    /** Turns two-factor authentication off. Not allowed for admins. */
    void disableTwoFactor(Long userId, TwoFactorDisableRequest request);
}
