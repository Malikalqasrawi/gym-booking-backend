package com.mycompany.gymbooking.phone;

/**
 * Texts one-time codes to phone numbers and checks them. Twilio Verify does this when its keys are
 * set; otherwise ConsolePhoneCodes writes the codes to the log. PhoneVerificationService adds the
 * limits (resend wait, codes per day, wrong tries) on top, the same for both.
 */
public interface PhoneCodes {

    enum Check {
        CORRECT,
        WRONG,
        /** There's no code to compare with any more: a new one has to be sent. */
        EXPIRED
    }

    /** Sends a new code to the number (international format). Earlier codes may stop working. */
    void send(String phone);

    Check check(String phone, String code);
}
