package com.mycompany.gymbooking.notification;

import com.mycompany.gymbooking.model.OutgoingEmail;
import com.mycompany.gymbooking.notification.OutboxNotificationSender.EmailQueued;
import com.mycompany.gymbooking.repository.OutgoingEmailRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the emails saved by OutboxNotificationSender: right after the transaction that saved them
 * commits, on a background thread. A failed attempt is retried later (after 1, 5, 15 and 60 minutes)
 * and the email is given up on after the fifth. Emails left over from before a restart are sent by
 * the same job that does the retries.
 */
@Component
@ConditionalOnExpression("'${app.notifications.mode:console}' != 'test'")
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    /** The wait after each failed attempt. After the last one, the email is marked FAILED. */
    static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1));
    static final int MAX_ATTEMPTS = RETRY_DELAYS.size() + 1;

    /** If an attempt hasn't finished by then (e.g. the server stopped), the email may be tried again. */
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 50;

    private final OutgoingEmailRepository emails;
    private final MailTransport transport;
    private final EmailLayout layout;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int keepDays;

    /** A single thread, so emails go out one at a time and never hold up a request. */
    private final ExecutorService sender = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "email-sender");
        thread.setDaemon(true);
        return thread;
    });

    public EmailDispatcher(OutgoingEmailRepository emails,
                           MailTransport transport,
                           EmailLayout layout,
                           TransactionTemplate transactions,
                           Clock clock,
                           @Value("${app.mail.keep-days}") int keepDays) {
        this.emails = emails;
        this.transport = transport;
        this.layout = layout;
        this.transactions = transactions;
        this.clock = clock;
        this.keepDays = keepDays;
    }

    /** Sends a new email as soon as the transaction that saved it has committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(EmailQueued queued) {
        inBackground(() -> deliver(queued.emailId(), LocalDateTime.now(clock)));
    }

    /** Every app.mail.retry-check-seconds: retries whose time has come, and emails left over from before a restart. */
    @Scheduled(initialDelayString = "${app.mail.retry-check-seconds}", fixedDelayString = "${app.mail.retry-check-seconds}",
            timeUnit = TimeUnit.SECONDS)
    public void sendDueEmails() {
        // On the email thread, so a slow mail server doesn't hold up the other scheduled jobs.
        inBackground(() -> sendDue(LocalDateTime.now(clock)));
    }

    /** Tries every email that's due at {@code now}. Returns how many were sent. */
    public int sendDue(LocalDateTime now) {
        List<Long> due = transactions.execute(status -> emails.findDueIds(now, PageRequest.of(0, BATCH_SIZE)));
        int sent = 0;
        for (Long id : due) {
            if (deliver(id, now)) {
                sent++;
            }
        }
        return sent;
    }

    /** Once a day: sent and failed emails are deleted after app.mail.keep-days. */
    @Scheduled(initialDelay = 2, fixedDelay = 24, timeUnit = TimeUnit.HOURS)
    public void deleteOldEmails() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(keepDays);
        Integer deleted = transactions.execute(status -> emails.deleteFinishedBefore(cutoff));
        if (deleted != null && deleted > 0) {
            log.info("Deleted {} sent or failed email(s) older than {} days", deleted, keepDays);
        }
    }

    @PreDestroy
    void stop() throws InterruptedException {
        sender.shutdown();
        sender.awaitTermination(15, TimeUnit.SECONDS);   // let the email being sent finish
    }

    /** One attempt to send one email. Returns true if it was sent. */
    private boolean deliver(Long id, LocalDateTime now) {
        OutgoingEmail email = claim(id, now);
        if (email == null) {
            return false;   // already sent, not due yet, or being sent by someone else
        }
        try {
            transport.send(email.getRecipient(), email.getSubject(), email.getBody(),
                    layout.html(email.getSubject(), email.getBody()));
        } catch (RuntimeException e) {
            recordFailure(email, describe(e), now);
            return false;
        }
        update(id, sent -> sent.markSent(now));
        return true;
    }

    /**
     * Marks the email as being sent, in its own short transaction. If another server or thread
     * claims it at the same moment, the version check lets only one of them through.
     */
    private OutgoingEmail claim(Long id, LocalDateTime now) {
        try {
            return transactions.execute(status -> {
                OutgoingEmail email = emails.findById(id).orElse(null);
                if (email == null || !email.isDue(now)) {
                    return null;
                }
                email.startAttempt(now.plus(ATTEMPT_TIMEOUT));
                return emails.saveAndFlush(email);
            });
        } catch (OptimisticLockingFailureException e) {
            return null;
        }
    }

    private void recordFailure(OutgoingEmail email, String error, LocalDateTime now) {
        int attempt = email.getAttempts();
        LocalDateTime retryAt = attempt < MAX_ATTEMPTS ? now.plus(RETRY_DELAYS.get(attempt - 1)) : null;
        update(email.getId(), failed -> failed.markAttemptFailed(error, retryAt));
        if (retryAt != null) {
            log.warn("Email #{} \"{}\" to {} wasn't sent (attempt {} of {}). Trying again at {}. {}",
                    email.getId(), email.getSubject(), email.getRecipient(), attempt, MAX_ATTEMPTS,
                    retryAt.toLocalTime().truncatedTo(ChronoUnit.SECONDS), error);
        } else {
            log.error("Email #{} \"{}\" to {} wasn't sent after {} attempts and won't be tried again. {}",
                    email.getId(), email.getSubject(), email.getRecipient(), attempt, error);
        }
    }

    private void update(Long id, Consumer<OutgoingEmail> change) {
        transactions.executeWithoutResult(status -> emails.findById(id).ifPresent(change));
    }

    private void inBackground(Runnable task) {
        try {
            sender.execute(task);
        } catch (RejectedExecutionException e) {
            // Shutting down: the emails stay PENDING and are sent after the next start.
        }
    }

    private static String describe(RuntimeException e) {
        if (e instanceof MailAuthenticationException) {
            return "The mail server rejected the login. Check spring.mail.username and spring.mail.password "
                    + "in local.properties (for Gmail, the password must be an app password).";
        }
        // The mail server's own answer, e.g. "451 4.3.0 Mail server busy, try again later"
        Exception cause = e instanceof MailSendException send && !send.getFailedMessages().isEmpty()
                ? send.getFailedMessages().values().iterator().next()
                : e;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage().strip();
    }
}
