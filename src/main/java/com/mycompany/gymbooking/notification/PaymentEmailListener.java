package com.mycompany.gymbooking.notification;

import com.mycompany.gymbooking.payment.BookingPaidEvent;
import com.mycompany.gymbooking.payment.BookingRefundedEvent;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends payment confirmation and refund emails. Handlers run after commit so no email goes out
 * for a payment that was rolled back.
 */
@Component
public class PaymentEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEmailListener.class);
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private final NotificationSender sender;

    public PaymentEmailListener(NotificationSender sender) {
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaid(BookingPaidEvent paid) {
        String cancelLine = paid.cancelUntil().isAfter(paid.paidAt())
                ? "Free cancellation (full refund) until " + paid.cancelUntil().format(DAY_AND_TIME) + "."
                : "The session starts soon, so it can no longer be cancelled.";
        safeSend(paid.memberEmail(), "Booking confirmed: " + paid.trainerName() + ", " + paid.when(),
                "Hi " + firstName(paid.memberName()) + ",\n\n"
                        + "We received your payment. Your session is confirmed.\n\n"
                        + "  Trainer:   " + paid.trainerName() + "\n"
                        + "  When:      " + paid.when() + "\n"
                        + "  Where:     " + paid.where() + "\n"
                        + "  Paid:      " + money(paid.amount(), paid.currency()) + " with " + paid.paymentMethod() + "\n"
                        + "  Booking:   #" + paid.bookingId() + "\n\n"
                        + cancelLine + "\n"
                        + "See you at the gym!");

        safeSend(paid.trainerEmail(), "Session confirmed: " + paid.memberName() + ", " + paid.when(),
                paid.memberName() + " paid for the session on " + paid.when() + ". It's confirmed.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefunded(BookingRefundedEvent refunded) {
        String reason = refunded.paidTooLate()
                ? "Your session with " + refunded.trainerName() + " on " + refunded.when()
                        + " had already expired or been cancelled when your payment arrived, so we gave the money back."
                : "You cancelled your session with " + refunded.trainerName() + " on " + refunded.when() + ".";
        safeSend(refunded.memberEmail(), "Refund: " + money(refunded.amount(), refunded.currency()),
                "Hi " + firstName(refunded.memberName()) + ",\n\n"
                        + reason + "\n\n"
                        + "  Refunded:  " + money(refunded.amount(), refunded.currency()) + " to " + refunded.paymentMethod() + "\n"
                        + "  Booking:   #" + refunded.bookingId() + "\n\n"
                        + "Banks usually show the money back within 5–10 days.");
    }

    /** The payment is already committed, so mail failures are logged rather than propagated. */
    private void safeSend(String to, String subject, String body) {
        try {
            sender.send(to, subject, body);
        } catch (RuntimeException e) {
            log.error("Could not send \"{}\" to {}: {}", subject, to, e.getMessage());
        }
    }

    /** Dinar amounts are always shown with 3 decimals. */
    private static String money(BigDecimal amount, String currency) {
        return amount.setScale(3).toPlainString() + " " + currency;
    }

    private static String firstName(String fullName) {
        return fullName.split(" ")[0];
    }
}
