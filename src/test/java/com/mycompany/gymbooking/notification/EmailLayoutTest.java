package com.mycompany.gymbooking.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The HTML version of an email, built from its text. */
class EmailLayoutTest {

    private final EmailLayout layout = new EmailLayout("Gym Booking");

    @Test
    @DisplayName("details, steps and codes get their own look, and other lines become paragraphs")
    void layout() {
        String html = layout.html("Booking confirmed", """
                Hi Malik,

                We received your payment.
                Your session is confirmed.

                  Trainer:   Sara Haddad
                  Paid:      20.000 JOD with Visa •••• 4242

                  1. Open the app.
                  2. Tap "I have an invite code".

                Your invite code is: 123456
                See you at the gym!""");

        assertTrue(html.contains(">Gym Booking</td>"), "the brand at the top");
        assertTrue(html.contains("<p style=\"margin:0 0 16px;\">Hi Malik,</p>"));
        assertTrue(html.contains("We received your payment.<br>\nYour session is confirmed.</p>"), "lines without a blank line between them stay one paragraph");
        assertTrue(html.contains(">Trainer</td><td style=\"padding:8px 16px;font-weight:600;\">Sara Haddad</td>"));
        assertTrue(html.contains(">20.000 JOD with Visa •••• 4242</td>"));
        assertTrue(html.contains("<li style=\"margin:0 0 4px;\">Open the app.</li>"));
        assertTrue(html.contains("<li style=\"margin:0 0 4px;\">Tap &quot;I have an invite code&quot;.</li>"));
        assertTrue(html.contains("Your invite code is:</p>"));
        assertTrue(html.contains("color:#c2410c;\">123456</div>"), "the code is shown large");
        assertTrue(html.contains("<p style=\"margin:0 0 16px;\">See you at the gym!</p>"));
        assertTrue(html.contains("overflow:hidden;\">We received your payment.</div>"), "inbox preview: the first line after the greeting");
    }

    @Test
    @DisplayName("text typed by users is escaped, so it can't add HTML to the email")
    void escapesText() {
        String html = layout.html("Note <b>", """
                Note: <script>alert(1)</script> & more
                  Note:      <img src=x onerror=alert(1)>""");

        assertTrue(html.contains("<title>Note &lt;b&gt;</title>"));
        assertTrue(html.contains("Note: &lt;script&gt;alert(1)&lt;/script&gt; &amp; more"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<img"));
    }
}
