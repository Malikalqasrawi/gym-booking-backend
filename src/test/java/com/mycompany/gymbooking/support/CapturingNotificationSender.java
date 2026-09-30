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
        List<Email> emails = to(recipient);
        for (int i = emails.size() - 1; i >= 0; i--) {
            Matcher matcher = CODE.matcher(emails.get(i).body());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("No verification code was emailed to " + recipient);
    }
}
