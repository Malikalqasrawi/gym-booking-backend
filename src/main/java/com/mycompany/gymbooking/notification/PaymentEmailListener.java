package com.mycompany.gymbooking.notification;

import com.mycompany.gymbooking.payment.BookingPaidEvent;
import com.mycompany.gymbooking.payment.BookingRefundedEvent;
import com.mycompany.gymbooking.payment.ChargeConversion;
import com.mycompany.gymbooking.payment.CurrencyUnits;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends payment confirmation and refund emails. The handlers run just before the payment's
 * transaction commits, so the emails are saved together with the payment: both or neither.
 */
@Component
public class PaymentEmailListener {

    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private final NotificationSender sender;

    public PaymentEmailListener(NotificationSender sender) {
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPaid(BookingPaidEvent paid) {
        String cancelLine = paid.cancelUntil().isAfter(paid.paidAt())
                ? "Free cancellation (full refund) until " + paid.cancelUntil().format(DAY_AND_TIME) + "."
                : "The session starts soon, so it can no longer be cancelled.";
        sender.send(paid.memberEmail(), "Booking confirmed: " + paid.trainerName() + ", " + paid.when(),
                "Hi " + firstName(paid.memberName()) + ",\n\n"
                        + "We received your payment. Your session is confirmed.\n\n"
                        + "  Trainer:   " + paid.trainerName() + "\n"
                        + "  When:      " + paid.when() + "\n"
                        + "  Where:     " + paid.where() + "\n"
                        + "  Paid:      " + paidAmount(paid) + " with " + paid.paymentMethod() + "\n"
                        + "  Booking:   #" + paid.bookingId() + "\n\n"
                        + cancelLine + "\n"
                        + "See you at the gym!");

        sender.send(paid.trainerEmail(), "Session confirmed: " + paid.memberName() + ", " + paid.when(),
                paid.memberName() + " paid for the session on " + paid.when() + ". It's confirmed.");
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onRefunded(BookingRefundedEvent refunded) {
        String session = refunded.trainerName() + " on " + refunded.when();
        String reason = switch (refunded.reason()) {
            case MEMBER_CANCELLED -> "You cancelled your session with " + session + ".";
            case GYM_CANCELLED -> "We're sorry, but the gym had to cancel your session with " + session + "."
                    + (refunded.note() == null ? "" : "\nReason: " + refunded.note())
                    + "\nYou get a full refund.";
            case PAID_TOO_LATE -> "Your session with " + session
                    + " had already expired or been cancelled when your payment arrived, so we gave the money back.";
        };
        sender.send(refunded.memberEmail(), "Refund: " + money(refunded.amount(), refunded.currency()),
                "Hi " + firstName(refunded.memberName()) + ",\n\n"
                        + reason + "\n\n"
                        + "  Refunded:  " + money(refunded.amount(), refunded.currency()) + " to " + refunded.paymentMethod() + "\n"
                        + "  Booking:   #" + refunded.bookingId() + "\n\n"
                        + "Banks usually show the money back within 5–10 days.");
    }

    /** "28.21 USD (20.000 JOD)" when charged in another currency, otherwise "20.000 JOD". */
    private static String paidAmount(BookingPaidEvent paid) {
        String charged = money(paid.amount(), paid.currency());
        return paid.currency().equalsIgnoreCase(ChargeConversion.PRICE_CURRENCY)
                ? charged
                : charged + " (" + money(paid.price(), ChargeConversion.PRICE_CURRENCY) + ")";
    }

    private static String money(BigDecimal amount, String currency) {
        return amount.setScale(CurrencyUnits.decimals(currency)).toPlainString() + " " + currency;
    }

    private static String firstName(String fullName) {
        return fullName.split(" ")[0];
    }
}
