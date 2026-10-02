package com.mycompany.gymbooking.support;

import com.mycompany.gymbooking.notification.NotificationSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Records emails instead of sending them, so tests can read verification codes and check receipts. */
public final class CapturingNotificationSender implements NotificationSender {

    public record Email(String to, String subject, String body) {
    }

    private static final Pattern CODE = Pattern.compile("verification code is: (\\d{6})");
    private static final Pattern INVITE_CODE = Pattern.compile("invite code is: (\\d{6})");

    private final List<Email> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String recipient, String subject, String body) {
        sent.add(new Email(recipient, subject, body));
    }

    public List<Email> to(String recipient) {
        return sent.stream().filter(e -> e.to().equalsIgnoreCase(recipient)).toList();
    }

    public long count(String recipient, String subjectStart) {
        return to(recipient).stream().filter(e -> e.subject().startsWith(subjectStart)).count();
    }

    public String latestVerificationCode(String recipient) {
        return latestMatch(recipient, CODE, "verification code");
    }

    public String latestInviteCode(String recipient) {
        return latestMatch(recipient, INVITE_CODE, "invite code");
    }

    public String latestBody(String recipient, String subjectStart) {
        List<Email> emails = to(recipient);
        for (int i = emails.size() - 1; i >= 0; i--) {
            if (emails.get(i).subject().startsWith(subjectStart)) {
                return emails.get(i).body();
            }
        }
        throw new AssertionError("No \"" + subjectStart + "\" email was sent to " + recipient);
    }

    private String latestMatch(String recipient, Pattern pattern, String what) {
        List<Email> emails = to(recipient);
        for (int i = emails.size() - 1; i >= 0; i--) {
            Matcher matcher = pattern.matcher(emails.get(i).body());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("No " + what + " was emailed to " + recipient);
    }
}
