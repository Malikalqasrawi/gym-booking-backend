package com.mycompany.gymbooking.notification;

import com.mycompany.gymbooking.model.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Emails users when something that protects their account changes, so they notice if it wasn't
 * them. Each email says what to do in that case. Called inside the change's transaction, so an
 * alert goes out only if the change was saved.
 */
@Component
public class SecurityAlerts {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final String FORGOT_PASSWORD = "\"Forgot password?\" on the login screen";

    private final NotificationSender sender;
    private final Clock clock;

    public SecurityAlerts(NotificationSender sender, Clock clock) {
        this.sender = sender;
        this.clock = clock;
    }

    /** Changed in Profile, which needs the current password. */
    public void passwordChanged(User user) {
        alert(user, "Your Gym Booking password was changed",
                "Your password was changed in the app, and your other devices were logged out.",
                "someone knows your password. Reset it right away with " + FORGOT_PASSWORD
                        + ". That logs out every device.");
    }

    /** Set with a code from this email address: a forgotten password, or a first password after Google sign-up. */
    public void passwordReset(User user, boolean hadPassword) {
        if (hadPassword) {
            alert(user, "Your Gym Booking password was reset",
                    "Your password was reset with a code sent to this email address, and every device was logged out.",
                    "someone can read your email. Change your email password first, then reset your Gym Booking "
                            + "password with " + FORGOT_PASSWORD + ".");
        } else {
            alert(user, "A password was added to your Gym Booking account",
                    "You can now log in with your email address and password, as well as with Google.",
                    "someone can read your email. Change your email password first, then reset your Gym Booking "
                            + "password with " + FORGOT_PASSWORD + ".");
        }
    }

    public void twoFactorOn(User user) {
        alert(user, "Two-factor authentication is on",
                "From now on, logging in also needs a code from your authenticator app. "
                        + "Keep your recovery codes somewhere safe: they're the way in if you lose your phone.",
                "someone knows your password and connected their own authenticator app. Contact the gym, "
                        + "and reset your password with " + FORGOT_PASSWORD + ".");
    }

    /** Set up again on another phone: the old app's codes and the old recovery codes stopped working. */
    public void twoFactorMoved(User user) {
        alert(user, "Two-factor authentication moved to a new phone",
                "Your login codes now come from the authenticator app you just set up. Codes from the old app "
                        + "and your old recovery codes no longer work.",
                "someone knows your password and has your authenticator app or a recovery code. Contact the gym, "
                        + "and reset your password with " + FORGOT_PASSWORD + ".");
    }

    public void twoFactorOff(User user) {
        alert(user, "Two-factor authentication was turned off",
                "Logging in now needs only your password.",
                "someone knows your password and has your authenticator app or a recovery code. Reset your "
                        + "password with " + FORGOT_PASSWORD + ", then turn two-factor authentication back on in Profile.");
    }

    /** A first "Continue with Google" for an account that already existed. */
    public void googleLinked(User user) {
        alert(user, "Google sign-in was added to your account",
                "You can now log in with \"Continue with Google\", using the Google account for this email address.",
                "someone can use your Google account. Secure it at myaccount.google.com first, then reset your "
                        + "Gym Booking password with " + FORGOT_PASSWORD + ".");
    }

    private void alert(User user, String subject, String whatChanged, String ifItWasntYou) {
        sender.send(user.getEmail(), subject,
                "Hi " + user.getFullName().split(" ")[0] + ",\n\n"
                        + whatChanged + "\n\n"
                        + "  Account:   " + user.getEmail() + "\n"
                        + "  When:      " + LocalDateTime.now(clock).format(WHEN) + "\n\n"
                        + "If this was you, there's nothing else to do.\n"
                        + "If it wasn't, " + ifItWasntYou);
    }
}
