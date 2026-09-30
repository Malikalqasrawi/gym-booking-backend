package com.mycompany.gymbooking.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.gymbooking.exception.ConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The booking rules (the state machine in Booking.java), tested without Spring or a database:
 * we create plain Java objects and move a fake "now" forward.
 *
 * Story used by every test: the session is on Wed 7 Oct 2026, 10:00–11:00, requested on Thu 1 Oct at 09:00.
 */
class BookingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final LocalDate SESSION_DAY = LocalDate.of(2026, 10, 7);
    private static final LocalDateTime SESSION_START = LocalDateTime.of(SESSION_DAY, LocalTime.of(10, 0));

    private final Member member = new Member("Malik Test", "malik@test.com", "0790000000", "hash");
    private final Trainer trainer = newTrainer();

    private static Trainer newTrainer() {
        Trainer trainer = new Trainer("Sara Haddad", "sara@test.com", "0790000001", "hash", "Strength", "Bio", 6);
        trainer.assignToBranch(new Branch("Abdoun Branch", "Abdoun Circle", "Amman", 31.95, 35.88,
                "065000000", LocalTime.of(6, 0), LocalTime.of(23, 0)));
        trainer.changeHourlyRate(new BigDecimal("20.000"));
        return trainer;
    }

    /** A new request; the trainer has 24 h to answer. */
    private Booking newRequest() {
        return new Booking(member, trainer, SESSION_DAY, LocalTime.of(10, 0), 60,
                trainer.priceFor(60), "First session", NOW, NOW.plusHours(24));
    }

    /** Accepted at NOW, must be paid within 12 h. */
    private Booking acceptedBooking() {
        Booking booking = newRequest();
        booking.accept("See you!", NOW, NOW.plusHours(12));
        return booking;
    }

    private Booking paidBooking() {
        Booking booking = acceptedBooking();
        booking.markPaid(NOW.plusHours(1), SESSION_START.minusHours(24));
        return booking;
    }

    private static String codeOf(Runnable action) {
        return assertThrows(ConflictException.class, action::run).getCode();
    }

    // ---- Prices ----

    @Test
    @DisplayName("price = hourly rate × duration (20 JOD/h)")
    void priceFollowsTheHourlyRate() {
        assertEquals(new BigDecimal("10.000"), trainer.priceFor(30));
        assertEquals(new BigDecimal("15.000"), trainer.priceFor(45));
        assertEquals(new BigDecimal("20.000"), trainer.priceFor(60));
        assertEquals(new BigDecimal("30.000"), trainer.priceFor(90));
    }

    // ---- Requests ----

    @Test
    @DisplayName("a new request waits for the trainer and keeps the time taken")
    void newRequestHoldsTheSlot() {
        Booking booking = newRequest();
        assertEquals(BookingStatus.REQUESTED, booking.statusAt(NOW));
        assertTrue(booking.holdsSlotAt(NOW));
        assertFalse(booking.canBePaidAt(NOW), "can't pay before the trainer accepts");
        assertEquals("NOT_ACCEPTED_YET", codeOf(() -> booking.requirePayable(NOW)));
    }

    @Test
    @DisplayName("an unanswered request counts as EXPIRED at its deadline and frees the time")
    void unansweredRequestExpires() {
        Booking booking = newRequest();
        LocalDateTime deadline = NOW.plusHours(24);
        assertEquals(BookingStatus.REQUESTED, booking.statusAt(deadline.minusMinutes(1)));
        assertEquals(BookingStatus.EXPIRED, booking.statusAt(deadline));
        assertFalse(booking.holdsSlotAt(deadline));
        assertEquals("REQUEST_EXPIRED", codeOf(() -> booking.accept(null, deadline, deadline.plusHours(12))));
    }

    @Test
    @DisplayName("a request can be answered only once")
    void requestAnsweredOnlyOnce() {
        Booking booking = acceptedBooking();
        assertEquals("BOOKING_NOT_PENDING", codeOf(() -> booking.accept(null, NOW, NOW.plusHours(12))));
        assertEquals("BOOKING_NOT_PENDING", codeOf(() -> booking.reject(null, NOW)));
    }

    // ---- Paying ----

    @Test
    @DisplayName("accepted: can be paid until the pay deadline, then EXPIRED")
    void acceptedBookingMustBePaidInTime() {
        Booking booking = acceptedBooking();
        LocalDateTime payBy = NOW.plusHours(12);
        assertEquals(payBy, booking.payDeadline());
        assertTrue(booking.canBePaidAt(payBy.minusMinutes(1)));
        assertEquals(BookingStatus.EXPIRED, booking.statusAt(payBy));
        assertFalse(booking.canBePaidAt(payBy));
        assertEquals("BOOKING_EXPIRED", codeOf(() -> booking.requirePayable(payBy)));
    }

    @Test
    @DisplayName("paid: status PAID, can't be paid twice, still holds the time")
    void paidBookingIsConfirmed() {
        Booking booking = paidBooking();
        assertEquals(BookingStatus.PAID, booking.statusAt(NOW.plusHours(2)));
        assertTrue(booking.holdsSlotAt(NOW.plusHours(2)));
        assertEquals("ALREADY_PAID", codeOf(() -> booking.requirePayable(NOW.plusHours(2))));
        // PAID never expires, even after the old pay deadline
        assertEquals(BookingStatus.PAID, booking.statusAt(NOW.plusHours(20)));
    }

    // ---- Cancelling ----

    @Test
    @DisplayName("not paid yet: can be cancelled until the session starts")
    void unpaidBookingCancelUntilStart() {
        Booking booking = newRequest();
        assertEquals(SESSION_START, booking.cancelDeadline(NOW));
        booking.cancelByMember(NOW);
        assertEquals(BookingStatus.CANCELLED, booking.statusAt(NOW));
        assertNull(booking.cancelDeadline(NOW));
        assertEquals("BOOKING_NOT_CANCELLABLE", codeOf(() -> booking.cancelByMember(NOW)));
    }

    @Test
    @DisplayName("paid: can be cancelled until 24 h before the start")
    void paidBookingCancelUntil24HoursBefore() {
        Booking booking = paidBooking();
        LocalDateTime lastMoment = SESSION_START.minusHours(24);
        assertEquals(lastMoment, booking.cancelDeadline(NOW.plusHours(2)));
        assertTrue(booking.canBeCancelledAt(lastMoment.minusMinutes(1)));
        assertFalse(booking.canBeCancelledAt(lastMoment));
    }

    @Test
    @DisplayName("paid: cancelling in the last 24 h is refused with a clear message")
    void paidBookingTooLateToCancel() {
        Booking booking = paidBooking();
        ConflictException error = assertThrows(ConflictException.class,
                () -> booking.cancelByMember(SESSION_START.minusHours(3)));
        assertEquals("TOO_LATE_TO_CANCEL", error.getCode());
        assertTrue(error.getMessage().contains("24 hours"), error.getMessage());
        assertEquals(BookingStatus.PAID, booking.statusAt(SESSION_START.minusHours(3)), "still paid");
    }

    @Test
    @DisplayName("nothing can be cancelled once the session has started")
    void cannotCancelStartedSession() {
        // (a request whose answer deadline is after the start, so it's still "waiting" at 10:00)
        Booking request = new Booking(member, trainer, SESSION_DAY, LocalTime.of(10, 0), 60,
                trainer.priceFor(60), null, NOW, SESSION_START.plusHours(1));
        assertFalse(request.canBeCancelledAt(SESSION_START));
        assertEquals("SESSION_STARTED", codeOf(() -> request.cancelByMember(SESSION_START)));
    }

    // ---- Clean-up job ----

    @Test
    @DisplayName("expireIfOverdue changes only overdue requests / unpaid bookings")
    void expireIfOverdue() {
        Booking request = newRequest();
        assertFalse(request.expireIfOverdue(NOW.plusHours(1)), "not overdue yet");
        assertTrue(request.expireIfOverdue(NOW.plusHours(24)));

        Booking unpaid = acceptedBooking();
        assertTrue(unpaid.expireIfOverdue(NOW.plusHours(12)));

        Booking paid = paidBooking();
        assertFalse(paid.expireIfOverdue(NOW.plusDays(3)), "paid bookings never expire");
    }

    @Test
    @DisplayName("overlap check: 10:00–11:00 vs other times")
    void overlaps() {
        Booking booking = newRequest();
        assertTrue(booking.overlaps(LocalTime.of(10, 30), LocalTime.of(11, 30)));
        assertTrue(booking.overlaps(LocalTime.of(9, 30), LocalTime.of(10, 30)));
        assertFalse(booking.overlaps(LocalTime.of(11, 0), LocalTime.of(12, 0)), "back-to-back is fine");
        assertFalse(booking.overlaps(LocalTime.of(9, 0), LocalTime.of(10, 0)));
    }
}
