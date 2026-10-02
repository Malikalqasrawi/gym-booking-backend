package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.BookingStatus;
import com.mycompany.gymbooking.model.Payment;
import com.mycompany.gymbooking.notification.NotificationSender;
import com.mycompany.gymbooking.repository.BookingRepository;
import com.mycompany.gymbooking.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emails the member and the trainer a reminder app.reminders.hours-before (24) hours before a paid
 * session. Sessions paid within that time don't get one, because the payment confirmation was
 * just sent.
 */
@Component
public class SessionReminders {

    private static final Logger log = LoggerFactory.getLogger(SessionReminders.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationSender notificationSender;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int hoursBefore;

    public SessionReminders(BookingRepository bookingRepository,
                            PaymentRepository paymentRepository,
                            NotificationSender notificationSender,
                            TransactionTemplate transactions,
                            Clock clock,
                            @Value("${app.reminders.hours-before}") int hoursBefore) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.notificationSender = notificationSender;
        this.transactions = transactions;
        this.clock = clock;
        this.hoursBefore = hoursBefore;
    }

    @Scheduled(initialDelay = 90, fixedDelay = 300, timeUnit = TimeUnit.SECONDS)
    public void sendDueReminders() {
        sendDue(LocalDateTime.now(clock));
    }

    /**
     * Sends the reminders that are due at {@code now}. Like the expiry job, each booking gets its own
     * short transaction. Returns how many bookings got a reminder.
     */
    public int sendDue(LocalDateTime now) {
        LocalDate lastDay = now.plusHours(hoursBefore).toLocalDate();
        List<Long> candidates = transactions.execute(status ->
                bookingRepository.findReminderCandidateIds(now.toLocalDate(), lastDay));

        int reminded = 0;
        for (Long id : candidates) {
            if (Boolean.TRUE.equals(transactions.execute(status -> remind(id, now)))) {
                reminded++;
            }
        }
        if (reminded > 0) {
            log.info("Sent reminders for {} session(s)", reminded);
        }
        return reminded;
    }

    /** Locks the booking so two servers can't both send its reminder. Returns true if one was sent. */
    private boolean remind(Long bookingId, LocalDateTime now) {
        Booking booking = bookingRepository.findLockedById(bookingId).orElse(null);
        if (booking == null || booking.getReminderCheckedAt() != null || booking.statusAt(now) != BookingStatus.PAID) {
            return false;
        }
        LocalDateTime startsAt = booking.getStartsAt();
        if (startsAt.isAfter(now.plusHours(hoursBefore))) {
            return false;   // not yet
        }
        booking.markReminderChecked(now);
        if (!startsAt.isAfter(now) || paidWithinReminderTime(booking)) {
            return false;   // already started (e.g. the server was off), or just confirmed
        }

        String at = relativeDay(booking.getDate(), now.toLocalDate()) + " at " + booking.getStartTime();
        String member = booking.getMember().getFullName();
        String trainer = booking.getTrainer().getFullName();

        notificationSender.send(booking.getMember().getEmail(), "Reminder: your session " + at,
                "Hi " + firstName(member) + ",\n\n"
                        + "Your session with " + trainer + " is " + at + ".\n\n"
                        + "  Trainer:   " + trainer + "\n"
                        + "  When:      " + BookingTexts.when(booking) + "\n"
                        + "  Where:     " + BookingTexts.where(booking) + "\n"
                        + "  Booking:   #" + booking.getId() + "\n\n"
                        + "Please arrive a few minutes early. See you at the gym!");

        notificationSender.send(booking.getTrainer().getEmail(), "Reminder: session with " + member + " " + at,
                "Hi " + firstName(trainer) + ",\n\n"
                        + "You have a paid session with " + member + " " + at + ".\n\n"
                        + "  Member:    " + member + "\n"
                        + "  When:      " + BookingTexts.when(booking) + "\n"
                        + "  Where:     " + BookingTexts.where(booking) + "\n"
                        + (booking.getMemberNote() == null ? "" : "  Note:      " + booking.getMemberNote() + "\n")
                        + "  Booking:   #" + booking.getId());
        return true;
    }

    private boolean paidWithinReminderTime(Booking booking) {
        LocalDateTime reminderTime = booking.getStartsAt().minusHours(hoursBefore);
        return paymentRepository.findByBookingId(booking.getId())
                .map(Payment::getPaidAt)
                .map(paidAt -> paidAt.isAfter(reminderTime))
                .orElse(false);
    }

    /** "today", "tomorrow" or e.g. "on Thu 8 Oct". */
    private static String relativeDay(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return "today";
        }
        return date.equals(today.plusDays(1)) ? "tomorrow" : "on " + date.format(DAY);
    }

    private static String firstName(String fullName) {
        return fullName.split(" ")[0];
    }
}
