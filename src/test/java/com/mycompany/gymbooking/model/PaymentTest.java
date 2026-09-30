package com.mycompany.gymbooking.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.gymbooking.exception.ConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Payment's rules: PENDING → SUCCEEDED → REFUNDED, in that order only. */
class PaymentTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

    private Payment newPayment() {
        Trainer trainer = new Trainer("Lina Nasser", "lina@test.com", "0790000002", "hash", "Pilates", "Bio", 5);
        trainer.changeHourlyRate(new BigDecimal("22.000"));
        trainer.assignToBranch(new Branch("Sweifieh Branch", "Wakalat Street", "Amman", 31.95, 35.86,
                "065000001", LocalTime.of(6, 0), LocalTime.of(22, 0)));
        Member member = new Member("Malik Test", "malik@test.com", "0790000000", "hash");
        Booking booking = new Booking(member, trainer, LocalDate.of(2026, 10, 7), LocalTime.of(8, 0), 90,
                trainer.priceFor(90), null, NOW, NOW.plusHours(24));
        return new Payment(booking, "stripe", NOW);
    }

    @Test
    @DisplayName("a new payment copies the booking's price, in JOD, and starts PENDING")
    void newPaymentCopiesThePrice() {
        Payment payment = newPayment();
        assertEquals(new BigDecimal("33.000"), payment.getAmount());
        assertEquals("JOD", payment.getCurrency());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals("Card", payment.getMethodLabel(), "no card details before paying");
    }

    @Test
    @DisplayName("succeeded, then refunded, with the card shown as 'Visa •••• 4242'")
    void succeedThenRefund() {
        Payment payment = newPayment();
        payment.markSucceeded("visa", "4242", NOW);
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals("Visa •••• 4242", payment.getMethodLabel());
        assertEquals(NOW, payment.getPaidAt());

        payment.markRefunded("re_123", NOW.plusDays(1));
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals("re_123", payment.getProviderRefundId());
    }

    @Test
    @DisplayName("can't refund what wasn't paid, and can't record a payment twice")
    void wrongOrderIsRefused() {
        Payment payment = newPayment();
        assertEquals("NOTHING_TO_REFUND",
                assertThrows(ConflictException.class, () -> payment.markRefunded("re_1", NOW)).getCode());
        payment.markSucceeded("mastercard", "4444", NOW);
        assertEquals("PAYMENT_ALREADY_RECORDED",
                assertThrows(ConflictException.class, () -> payment.markSucceeded("visa", "4242", NOW)).getCode());
    }

    @Test
    @DisplayName("card brand names: known ones spelled nicely, unknown ones capitalised")
    void cardLabels() {
        Payment mastercard = newPayment();
        mastercard.markSucceeded("mastercard", "4444", NOW);
        assertEquals("Mastercard •••• 4444", mastercard.getMethodLabel());

        Payment other = newPayment();
        other.markSucceeded("maestro", "1234", NOW);
        assertEquals("Maestro •••• 1234", other.getMethodLabel());
    }
}
