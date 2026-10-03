package com.mycompany.gymbooking.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.gymbooking.Gymbooking;
import com.mycompany.gymbooking.model.OutgoingEmail;
import com.mycompany.gymbooking.model.OutgoingEmail.Status;
import com.mycompany.gymbooking.repository.OutgoingEmailRepository;
import com.mycompany.gymbooking.support.FakeSmtp;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real delivery: the app runs with app.notifications.mode=email against FakeSmtp, so emails go
 * through the outbox, the background sender and SMTP, including refused emails and retries.
 */
class EmailDeliveryTest {

    private static final String GMAIL_ADDRESS = "gym.booking@example.com";
    private static final String APP_PASSWORD = "abcdefghijklmnop";
    private static final AtomicInteger NUMBER = new AtomicInteger();

    private static FakeSmtp smtp;
    private static ConfigurableApplicationContext backend;
    private static NotificationSender sender;
    private static EmailDispatcher dispatcher;
    private static OutgoingEmailRepository emails;
    private static TransactionTemplate transactions;

    @BeforeAll
    static void start() throws Exception {
        smtp = FakeSmtp.start(GMAIL_ADDRESS, APP_PASSWORD);
        byte[] jwtSecret = new byte[64];
        new SecureRandom().nextBytes(jwtSecret);

        backend = new SpringApplicationBuilder(Gymbooking.class).run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--app.jwt.secret=" + Base64.getEncoder().encodeToString(jwtSecret),
                "--app.admin.initial-password=AdminTest123",
                "--app.security.encryption-key=" + Base64.getEncoder().encodeToString(new byte[32]),
                "--app.notifications.mode=email",
                "--spring.mail.host=127.0.0.1",
                "--spring.mail.port=" + smtp.port(),
                "--spring.mail.username=" + GMAIL_ADDRESS,
                "--spring.mail.password=" + APP_PASSWORD,
                "--spring.mail.properties.mail.smtp.starttls.enable=false",     // FakeSmtp has no TLS
                "--spring.mail.properties.mail.smtp.starttls.required=false",
                "--app.mail.retry-check-seconds=3600",                          // the tests start retries themselves
                "--spring.main.banner-mode=off");
        sender = backend.getBean(NotificationSender.class);
        dispatcher = backend.getBean(EmailDispatcher.class);
        emails = backend.getBean(OutgoingEmailRepository.class);
        transactions = backend.getBean(TransactionTemplate.class);
    }

    @AfterAll
    static void stop() throws Exception {
        if (backend != null) {
            backend.close();
        }
        if (smtp != null) {
            smtp.close();
        }
    }

    @Test
    @DisplayName("an email is sent right after it's saved, as text and HTML, from Gym Booking")
    void sendsEmail() throws Exception {
        String to = newAddress();
        sender.send(to, "Your Gym Booking verification code",
                "Hi Malik,\n\nYour verification code is: 123456\nIt expires in 10 minutes.");

        FakeSmtp.Received email = waitForEmail(to);
        assertEquals(GMAIL_ADDRESS, email.from());
        InternetAddress from = (InternetAddress) email.message().getFrom()[0];
        assertEquals("Gym Booking", from.getPersonal());
        assertEquals(GMAIL_ADDRESS, from.getAddress());
        assertEquals("Your Gym Booking verification code", email.message().getSubject());
        assertTrue(part(email.message(), "text/plain").contains("Your verification code is: 123456"));
        assertTrue(part(email.message(), "text/html").contains(">123456</div>"));

        OutgoingEmail saved = waitForRow(to, row -> row.getStatus() == Status.SENT);
        assertEquals(1, saved.getAttempts());
        assertNull(saved.getBody(), "the text, with the code in it, isn't kept once it's sent");
    }

    @Test
    @DisplayName("an email from a transaction that rolls back is never sent")
    void rolledBack() throws Exception {
        String rolledBack = newAddress();
        String committed = newAddress();
        transactions.executeWithoutResult(status -> {
            sender.send(rolledBack, "Booking confirmed", "This booking was never saved.");
            status.setRollbackOnly();
        });
        transactions.executeWithoutResult(status -> sender.send(committed, "Booking confirmed", "This one was."));

        waitForRow(committed, row -> row.getStatus() == Status.SENT);   // one at a time, so the other would be sent by now
        assertTrue(smtp.to(rolledBack).isEmpty());
        assertTrue(rows(rolledBack).isEmpty());
    }

    @Test
    @DisplayName("a refused email is tried again a minute later, then five minutes later")
    void retries() throws Exception {
        String to = newAddress();
        smtp.refuseNext(2);
        sender.send(to, "Your session was accepted", "Sara Haddad accepted your session.");

        OutgoingEmail first = waitForRow(to, row -> row.getAttempts() == 1 && row.getLastError() != null);
        assertEquals(Status.PENDING, first.getStatus());
        assertTrue(first.getLastError().contains("try again later"), first.getLastError());
        Duration wait = Duration.between(first.getCreatedAt(), first.getNextAttemptAt());
        assertTrue(wait.compareTo(Duration.ofMinutes(1)) >= 0 && wait.compareTo(Duration.ofSeconds(70)) < 0, wait.toString());

        assertEquals(0, dispatcher.sendDue(first.getNextAttemptAt().minusSeconds(1)), "not before its time");
        assertEquals(0, dispatcher.sendDue(first.getNextAttemptAt()), "refused again");
        OutgoingEmail second = row(to);
        assertEquals(2, second.getAttempts());
        assertEquals(first.getNextAttemptAt().plusMinutes(5), second.getNextAttemptAt());

        assertEquals(1, dispatcher.sendDue(second.getNextAttemptAt()));
        assertEquals(Status.SENT, row(to).getStatus());
        assertEquals(1, smtp.to(to).size());
    }

    @Test
    @DisplayName("after five refused attempts the email is given up on, and its text is deleted")
    void givesUp() throws Exception {
        String to = newAddress();
        smtp.refuseNext(EmailDispatcher.MAX_ATTEMPTS);
        sender.send(to, "Your booking expired", "Nothing was charged.");

        OutgoingEmail email = waitForRow(to, row -> row.getAttempts() == 1 && row.getLastError() != null);
        for (int attempt = 2; attempt <= EmailDispatcher.MAX_ATTEMPTS; attempt++) {
            dispatcher.sendDue(email.getNextAttemptAt());
            email = row(to);
            assertEquals(attempt, email.getAttempts());
        }
        assertEquals(Status.FAILED, email.getStatus());
        assertNull(email.getNextAttemptAt());
        assertNull(email.getBody());
        assertTrue(smtp.to(to).isEmpty());
    }

    @Test
    @DisplayName("a refused login is reported clearly, and the email waits until the login works again")
    void wrongPassword() throws Exception {
        String to = newAddress();
        smtp.changePassword("a-revoked-app-password");
        try {
            sender.send(to, "Session cancelled", "Test Member cancelled the session.");
            OutgoingEmail refused = waitForRow(to, row -> row.getLastError() != null);
            assertTrue(refused.getLastError().contains("app password"), refused.getLastError());
            assertEquals(Status.PENDING, refused.getStatus());
        } finally {
            smtp.changePassword(APP_PASSWORD);
        }
        assertEquals(1, dispatcher.sendDue(row(to).getNextAttemptAt()));
        assertEquals(1, smtp.to(to).size());
    }

    @Test
    @DisplayName("sent and failed emails are deleted after 30 days; ones still waiting are kept")
    void deletesOldEmails() {
        LocalDateTime longAgo = LocalDateTime.now(backend.getBean(Clock.class)).minusDays(31);
        String sent = newAddress();
        String failed = newAddress();
        String waiting = newAddress();
        OutgoingEmail sentEmail = new OutgoingEmail(sent, "Old", "text", longAgo);
        sentEmail.markSent(longAgo);
        OutgoingEmail failedEmail = new OutgoingEmail(failed, "Old", "text", longAgo);
        failedEmail.markAttemptFailed("refused", null);
        OutgoingEmail waitingEmail = new OutgoingEmail(waiting, "Old", "text", longAgo);
        waitingEmail.startAttempt(longAgo.plusYears(10));   // not due, so the other tests don't send it
        emails.saveAll(List.of(sentEmail, failedEmail, waitingEmail));

        dispatcher.deleteOldEmails();
        assertTrue(rows(sent).isEmpty());
        assertTrue(rows(failed).isEmpty());
        assertEquals(1, rows(waiting).size());
        emails.deleteAll(rows(waiting));
    }

    private static String newAddress() {
        return "member" + NUMBER.incrementAndGet() + "@test.com";
    }

    private static List<OutgoingEmail> rows(String recipient) {
        return emails.findAll().stream().filter(email -> email.getRecipient().equals(recipient)).toList();
    }

    private static OutgoingEmail row(String recipient) {
        List<OutgoingEmail> found = rows(recipient);
        assertEquals(1, found.size(), found.toString());
        return found.get(0);
    }

    /** Emails are sent on a background thread, so tests wait (up to 5 seconds) for the outcome. */
    private static OutgoingEmail waitForRow(String recipient, Predicate<OutgoingEmail> condition) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            List<OutgoingEmail> found = rows(recipient);
            if (found.size() == 1 && condition.test(found.get(0))) {
                return found.get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The email to " + recipient + " didn't reach the expected state");
    }

    private static FakeSmtp.Received waitForEmail(String recipient) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            List<FakeSmtp.Received> received = smtp.to(recipient);
            if (!received.isEmpty()) {
                return received.get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No email reached " + recipient);
    }

    /** The text or HTML version, found inside the email's nested parts. */
    private static String part(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = part(multipart.getBodyPart(i), mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
