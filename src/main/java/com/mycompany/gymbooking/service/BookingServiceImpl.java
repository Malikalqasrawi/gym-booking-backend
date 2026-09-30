package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AvailabilityResponse;
import com.mycompany.gymbooking.dto.BookingRequest;
import com.mycompany.gymbooking.dto.BookingResponse;
import com.mycompany.gymbooking.exception.ConflictException;
import com.mycompany.gymbooking.exception.ForbiddenException;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.BookingStatus;
import com.mycompany.gymbooking.model.Member;
import com.mycompany.gymbooking.model.Payment;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.notification.NotificationSender;
import com.mycompany.gymbooking.repository.BookingRepository;
import com.mycompany.gymbooking.repository.PaymentRepository;
import com.mycompany.gymbooking.repository.TrainerRepository;
import com.mycompany.gymbooking.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The booking rules.
 *
 * Who may do what:
 *   - a member only sees and cancels THEIR bookings        (findByIdAndMemberId)
 *   - a trainer only sees and answers requests sent to THEM (findByIdAndTrainerId)
 *   - anything else → 404 "not found", as if the booking didn't exist
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);
    private static final DateTimeFormatter TIME_AND_DAY = DateTimeFormatter.ofPattern("HH:mm 'on' EEE d MMM", Locale.ENGLISH);

    /** A trainer's schedule: accepted (waiting for payment) and paid sessions. */
    private static final Set<BookingStatus> SCHEDULED = EnumSet.of(BookingStatus.ACCEPTED, BookingStatus.PAID);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final TrainerRepository trainerRepository;
    private final UserRepository userRepository;
    private final AvailabilityService availabilityService;
    private final PaymentService paymentService;
    private final NotificationSender notificationSender;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final long requestExpiryHours;
    private final long paymentWindowHours;
    private final int maxPendingPerMember;

    public BookingServiceImpl(BookingRepository bookingRepository,
                              PaymentRepository paymentRepository,
                              TrainerRepository trainerRepository,
                              UserRepository userRepository,
                              AvailabilityService availabilityService,
                              PaymentService paymentService,
                              NotificationSender notificationSender,
                              TransactionTemplate transactions,
                              Clock clock,
                              @Value("${app.booking.request-expiry-hours}") long requestExpiryHours,
                              @Value("${app.booking.payment-window-hours}") long paymentWindowHours,
                              @Value("${app.booking.max-pending-per-member}") int maxPendingPerMember) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.trainerRepository = trainerRepository;
        this.userRepository = userRepository;
        this.availabilityService = availabilityService;
        this.paymentService = paymentService;
        this.notificationSender = notificationSender;
        this.transactions = transactions;
        this.clock = clock;
        this.requestExpiryHours = requestExpiryHours;
        this.paymentWindowHours = paymentWindowHours;
        this.maxPendingPerMember = maxPendingPerMember;
    }

    // ==================================================================
    // MEMBER
    // ==================================================================

    /**
     * READ_COMMITTED: every query in this method sees the newest SAVED data, including a booking
     * another member saved while we were waiting for the lock in step 1.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse requestSession(Long memberId, BookingRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. Lock the trainer. If two members ask for this trainer at the same moment,
        //    the second one waits here until the first one has finished (like taking a ticket).
        Trainer trainer = trainerRepository.findLockedById(request.trainerId())
                .orElseThrow(() -> new NotFoundException("TRAINER_NOT_FOUND", "No trainer with id " + request.trainerId()));
        if (!trainer.hasHourlyRate()) {
            throw new ConflictException("TRAINER_NOT_BOOKABLE", "This trainer isn't taking bookings yet.");
        }

        // 2. Is that start time really free? We reuse the SAME rules that produced the times in the app
        //    (duration, 14 days ahead, working hours, branch hours, 60 min notice, already taken).
        AvailabilityResponse free = availabilityService.getAvailability(
                trainer.getId(), request.date(), request.durationMinutes());
        boolean offered = free.slots().stream().anyMatch(slot -> slot.start().equals(request.startTime()));
        if (!offered) {
            throw new ConflictException("SLOT_NOT_AVAILABLE", "That time is no longer available. Please pick another one.");
        }

        Member member = findMember(memberId);
        LocalTime end = request.startTime().plusMinutes(request.durationMinutes());

        // 3. A member can't be in two sessions at once (e.g. Sara and Omar at 10:00)
        boolean busy = bookingRepository.findByMemberIdAndDateAndStatusIn(memberId, request.date(), Booking.SLOT_HOLDING)
                .stream()
                .anyMatch(b -> b.holdsSlotAt(now) && b.overlaps(request.startTime(), end));
        if (busy) {
            throw new ConflictException("MEMBER_BUSY", "You already have a session at that time.");
        }

        // 4. Limit unanswered requests, so one person can't block every trainer's week
        long waiting = bookingRepository.findByMemberIdAndStatus(memberId, BookingStatus.REQUESTED)
                .stream()
                .filter(b -> b.holdsSlotAt(now))
                .count();
        if (waiting >= maxPendingPerMember) {
            throw new ConflictException("TOO_MANY_PENDING", "You already have " + waiting
                    + " requests waiting for an answer. Wait for a reply or cancel one first.");
        }

        // 5. Save. The trainer must answer within 24 h, or before the session starts if that's sooner.
        LocalDateTime startsAt = LocalDateTime.of(request.date(), request.startTime());
        LocalDateTime deadline = now.plusHours(requestExpiryHours);
        LocalDateTime respondBy = deadline.isBefore(startsAt) ? deadline : startsAt;

        Booking booking = new Booking(member, trainer, request.date(), request.startTime(), request.durationMinutes(),
                trainer.priceFor(request.durationMinutes()), request.note(), now, respondBy);
        bookingRepository.save(booking);

        notificationSender.send(trainer.getEmail(), "New session request",
                member.getFullName() + " asked for a session on " + BookingTexts.when(booking) + ".\n"
                        + (booking.getMemberNote() == null ? "" : "Note: " + booking.getMemberNote() + "\n")
                        + "Please accept or reject it in the app before " + booking.getRespondByAt().format(TIME_AND_DAY) + ".");

        return BookingResponse.from(booking, now);
    }

    /** With each booking's receipt: all payments are loaded in ONE extra query, not one per booking. */
    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> bookings = bookingRepository.findByMemberIdOrderByDateDescStartTimeDesc(memberId);

        List<Long> ids = bookings.stream().map(Booking::getId).toList();
        Map<Long, Payment> paymentByBooking = paymentRepository.findByBookingIdIn(ids).stream()
                .collect(Collectors.toMap(payment -> payment.getBooking().getId(), Function.identity()));

        return bookings.stream()
                .map(b -> BookingResponse.from(b, paymentByBooking.get(b.getId()), now))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse myBooking(Long memberId, Long bookingId) {
        Booking booking = memberBooking(memberId, bookingId);
        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        return BookingResponse.from(booking, payment, LocalDateTime.now(clock));
    }

    /**
     * The booking is LOCKED first: if a payment is being confirmed at the same moment, we wait for it,
     * then see PAID and refund. Otherwise we could cancel a booking without refunding its money.
     */
    @Override
    @Transactional
    public BookingResponse cancel(Long memberId, Long bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = bookingRepository.findLockedByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));

        boolean wasPaid = booking.statusAt(now) == BookingStatus.PAID;
        booking.cancelByMember(now);   // throws if it's not allowed (already rejected, started, < 24 h for paid...)

        // Paid → give the money back. If Stripe refuses, the exception undoes the cancel too (still PAID).
        Payment payment = wasPaid
                ? paymentService.refundCancelledBooking(booking, now)
                : paymentRepository.findByBookingId(bookingId).orElse(null);

        notificationSender.send(booking.getTrainer().getEmail(), "Session cancelled",
                booking.getMember().getFullName() + " cancelled the session on " + BookingTexts.when(booking) + "."
                        + (wasPaid ? " The member was refunded." : ""));
        return BookingResponse.from(booking, payment, now);
    }

    // ==================================================================
    // TRAINER
    // ==================================================================

    /** Requests waiting for an answer, the most urgent (closest deadline) first. */
    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> pendingRequests(Long trainerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return bookingRepository.findByTrainerIdAndStatusOrderByRespondByAtAsc(trainerId, BookingStatus.REQUESTED)
                .stream()
                .filter(b -> b.holdsSlotAt(now))              // hide the ones that just expired
                .map(b -> BookingResponse.from(b, now))
                .toList();
    }

    /** Accepted (waiting for payment) and paid sessions that haven't finished yet, soonest first. */
    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> upcomingSchedule(Long trainerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return bookingRepository
                .findByTrainerIdAndStatusInAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
                        trainerId, SCHEDULED, now.toLocalDate())
                .stream()
                .filter(b -> b.holdsSlotAt(now) && b.getEndsAt().isAfter(now))   // hides unpaid ones past their deadline
                .map(b -> BookingResponse.from(b, now))
                .toList();
    }

    @Override
    @Transactional
    public BookingResponse accept(Long trainerId, Long bookingId, String message) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = trainerBooking(trainerId, bookingId);

        // The member must pay within 12 h, or before the session starts if that's sooner
        LocalDateTime deadline = now.plusHours(paymentWindowHours);
        LocalDateTime payBy = deadline.isBefore(booking.getStartsAt()) ? deadline : booking.getStartsAt();

        booking.accept(message, now, payBy);   // throws if it isn't waiting for an answer any more

        notificationSender.send(booking.getMember().getEmail(), "Your session was accepted 🎉",
                booking.getTrainer().getFullName() + " accepted your session on " + BookingTexts.when(booking) + ".\n"
                        + (booking.getTrainerReply() == null ? "" : "Message: " + booking.getTrainerReply() + "\n")
                        + "Price: " + booking.getPrice().stripTrailingZeros().toPlainString() + " JOD.\n"
                        + "Please pay in the app before " + payBy.format(TIME_AND_DAY)
                        + " to confirm it. After that, the time is released.");
        return BookingResponse.from(booking, now);
    }

    @Override
    @Transactional
    public BookingResponse reject(Long trainerId, Long bookingId, String message) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = trainerBooking(trainerId, bookingId);

        booking.reject(message, now);

        notificationSender.send(booking.getMember().getEmail(), "Your session request was declined",
                booking.getTrainer().getFullName() + " can't take the session on " + BookingTexts.when(booking) + ".\n"
                        + (booking.getTrainerReply() == null ? "" : "Reason: " + booking.getTrainerReply() + "\n")
                        + "That time is free again, and you can pick another one in the app.");
        return BookingResponse.from(booking, now);
    }

    // ==================================================================
    // HOUSEKEEPING
    // ==================================================================

    /**
     * Not @Transactional on purpose: each booking gets its OWN small transaction (TransactionTemplate),
     * and only that one row is locked. So one busy booking (e.g. a payment being confirmed right now)
     * can't block or undo the others, and members/trainers are never locked out while the job runs.
     */
    @Override
    public int expireOverdue() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> overdue = transactions.execute(status -> bookingRepository.findOverdueIds(now));

        int expired = 0;
        for (Long id : overdue) {
            Boolean done = transactions.execute(status -> expireOne(id, now));
            if (Boolean.TRUE.equals(done)) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Expired {} booking(s): not answered or not paid in time", expired);
        }
        return expired;
    }

    /** Runs inside its own transaction. The lock makes sure a payment confirmed a second ago wins. */
    private boolean expireOne(Long bookingId, LocalDateTime now) {
        Booking booking = bookingRepository.findLockedById(bookingId).orElse(null);
        if (booking == null || !booking.expireIfOverdue(now)) {
            return false;   // paid / cancelled in the meantime
        }
        String trainer = booking.getTrainer().getFullName();
        if (booking.getRespondedAt() == null) {
            // Nobody answered the request
            notificationSender.send(booking.getMember().getEmail(), "Your session request expired",
                    trainer + " didn't answer your request for " + BookingTexts.when(booking)
                            + " in time, so it was cancelled. Please pick another time in the app.");
        } else {
            // Accepted, but not paid in time
            notificationSender.send(booking.getMember().getEmail(), "Your booking expired",
                    "Your session with " + trainer + " on " + BookingTexts.when(booking)
                            + " wasn't paid in time, so the time was released. Nothing was charged.");
            notificationSender.send(booking.getTrainer().getEmail(), "Unpaid session released",
                    booking.getMember().getFullName() + " didn't pay for the session on " + BookingTexts.when(booking)
                            + ", so that time is free again.");
        }
        return true;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Booking `bookingId`, but only if it belongs to this member. Otherwise 404. */
    private Booking memberBooking(Long memberId, Long bookingId) {
        return bookingRepository.findByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));
    }

    /** Booking `bookingId`, but only if it was sent to this trainer. Otherwise 404. */
    private Booking trainerBooking(Long trainerId, Long bookingId) {
        return bookingRepository.findByIdAndTrainerId(bookingId, trainerId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));
    }

    private Member findMember(Long memberId) {
        return userRepository.findById(memberId)
                .filter(Member.class::isInstance)
                .map(Member.class::cast)
                .orElseThrow(() -> new ForbiddenException("MEMBERS_ONLY", "Only members can request sessions"));
    }
}
