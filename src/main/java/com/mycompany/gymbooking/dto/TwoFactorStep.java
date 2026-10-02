package com.mycompany.gymbooking.dto;

/** What the app must ask for after a correct password, when the account uses two-factor login. */
public enum TwoFactorStep {
    /** A code from the authenticator app, or a recovery code. */
    CODE_REQUIRED,
    /** An admin who hasn't set up an authenticator app yet must do it before logging in. */
    SETUP_REQUIRED
}
