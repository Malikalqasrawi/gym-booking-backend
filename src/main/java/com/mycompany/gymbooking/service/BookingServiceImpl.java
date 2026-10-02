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
import com.mycompany.gymbooking.model.Review;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.notification.NotificationSender;
import com.mycompany.gymbooking.payment.RefundReason;
import com.mycompany.gymbooking.repository.BookingRepository;
import com.mycompany.gymbooking.repository.PaymentRepository;
import com.mycompany.gymbooking.repository.ReviewRepository;
import com.mycompany.gymbooking.repository.TrainerRepository;
import com.mycompany.gymbooking.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
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
 * Booking lifecycle for members and trainers. Lookups are scoped to the caller's member or trainer
 * id, so another user's booking returns 404 as if it didn't exist.
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);
    private static final DateTimeFormatter TIME_AND_DAY = DateTimeFormatter.ofPattern("HH:mm 'on' EEE d MMM", Locale.ENGLISH);

    private static final Set<BookingStatus> SCHEDULED = EnumSet.of(BookingStatus.ACCEPTED, BookingStatus.PAID);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
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
    private final long daysToReview;

    public BookingServiceImpl(BookingRepository bookingRepository,
                              PaymentRepository paymentRepository,
                              ReviewRepository reviewRepository,
                              TrainerRepository trainerRepository,
                              UserRepository userRepository,
                              AvailabilityService availabilityService,
                              PaymentService paymentService,
                              NotificationSender notificationSender,
                              TransactionTemplate transactions,
                              Clock clock,
                              @Value("${app.booking.request-expiry-hours}") long requestExpiryHours,
                              @Value("${app.booking.payment-window-hours}") long paymentWindowHours,
                              @Value("${app.booking.max-pending-per-member}") int maxPendingPerMember,
                              @Value("${app.reviews.days-to-review}") long daysToReview) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.reviewRepository = reviewRepository;
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
        this.daysToReview = daysToReview;
    }

    /** READ_COMMITTED so the checks after the trainer lock see bookings committed while waiting for it. */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse requestSession(Long memberId, BookingRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);

        // Pessimistic lock on the trainer serializes concurrent requests for them, preventing double booking.
        Trainer trainer = trainerRepository.findLockedById(request.trainerId())
                .orElseThrow(() -> new NotFoundException("TRAINER_NOT_FOUND",
                        "This trainer is no longer available. Please pick another one."));
        if (!trainer.isBookable()) {
            throw new ConflictException("TRAINER_NOT_BOOKABLE", "This trainer isn't taking bookings.");
        }

        // Re-validate the slot with the same rules that produced the offered times.
        AvailabilityResponse free = availabilityService.getAvailability(
                trainer.getId(), request.date(), request.durationMinutes());
        boolean offered = free.slots().stream().anyMatch(slot -> slot.start().equals(request.startTime()));
        if (!offered) {
            throw new ConflictException("SLOT_NOT_AVAILABLE", "That time is no longer available. Please pick another one.");
        }

        Member member = findMember(memberId);
        if (!member.isPhoneVerified()) {
            // So the trainer and the gym can reach the member at a number that really is theirs.
            throw new ForbiddenException("PHONE_NOT_VERIFIED",
                    "Please confirm your phone number first. We'll text you a code.");
        }
        LocalTime end = request.startTime().plusMinutes(request.durationMinutes());

        boolean busy = bookingRepository.findByMemberIdAndDateAndStatusIn(memberId, request.date(), Booking.SLOT_HOLDING)
                .stream()
                .anyMatch(b -> b.holdsSlotAt(now) && b.overlaps(request.startTime(), end));
        if (busy) {
            throw new ConflictException("MEMBER_BUSY", "You already have a session at that time.");
        }

        // Pending requests hold slots, so cap them to stop one member blocking trainers' calendars.
        long waiting = bookingRepository.findByMemberIdAndStatus(memberId, BookingStatus.REQUESTED)
                .stream()
                .filter(b -> b.holdsSlotAt(now))
                .count();
        if (waiting >= maxPendingPerMember) {
            throw new ConflictException("TOO_MANY_PENDING", "You already have " + waiting
                    + " requests waiting for an answer. Wait for a reply or cancel one first.");
        }

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

    /** Includes each booking's payment and review, loaded in one query each rather than one per booking. */
    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> bookings = bookingRepository.findByMemberIdOrderByDateDescStartTimeDesc(memberId);

        List<Long> ids = bookings.stream().map(Booking::getId).toList();
        Map<Long, Payment> paymentByBooking = paymentRepository.findByBookingIdIn(ids).stream()
                .collect(Collectors.toMap(payment -> payment.getBooking().getId(), Function.identity()));
        Map<Long, Review> reviewByBooking = reviewRepository.findByBookingIdIn(ids).stream()
                .collect(Collectors.toMap(review -> review.getBooking().getId(), Function.identity()));

        return bookings.stream()
                .map(b -> BookingResponse.from(b, paymentByBooking.get(b.getId()), reviewByBooking.get(b.getId()),
                        b.canBeReviewedAt(now, daysToReview), now))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse myBooking(Long memberId, Long bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = memberBooking(memberId, bookingId);
        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        Review review = reviewRepository.findByBookingIdIn(List.of(bookingId)).stream().findFirst().orElse(null);
        return BookingResponse.from(booking, payment, review, booking.canBeReviewedAt(now, daysToReview), now);
    }

    /**
     * Locks the booking so a concurrent payment confirmation completes first and is seen as PAID;
     * otherwise a just-paid booking could be cancelled without a refund.
     */
    @Override
    @Transactional
    public BookingResponse cancel(Long memberId, Long bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = bookingRepository.findLockedByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));

        boolean wasPaid = booking.statusAt(now) == BookingStatus.PAID;
        booking.cancelByMember(now);

        // If the Stripe refund fails, the exception rolls back the cancellation as well.
        Payment payment = wasPaid
                ? paymentService.refundCancelledBooking(booking, RefundReason.MEMBER_CANCELLED, now)
                : paymentRepository.findByBookingId(bookingId).orElse(null);

        notificationSender.send(booking.getTrainer().getEmail(), "Session cancelled",
                booking.getMember().getFullName() + " cancelled the session on " + BookingTexts.when(booking) + "."
                        + (wasPaid ? " The member was refunded." : ""));
        return BookingResponse.from(booking, payment, now);
    }

    @Override
    @Transactional
    public GymCancellations cancelUpcomingForTrainer(Long trainerId, String note) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> ids = bookingRepository.findIdsByTrainerFrom(trainerId, Booking.SLOT_HOLDING, now.toLocalDate());
        // The trainer is leaving, so only the members are told.
        GymCancellations result = cancelAllByGym(ids, note, now, false);
        if (result.cancelled() > 0) {
            log.info("Cancelled {} booking(s) of trainer {} ({} refunded)", result.cancelled(), trainerId, result.refunded());
        }
        return result;
    }

    @Override
    @Transactional
    public GymCancellations cancelByGym(Collection<Long> bookingIds, String note) {
        return cancelAllByGym(bookingIds, note, LocalDateTime.now(clock), true);
    }

    @Override
    @Transactional
    public void cancelByGym(Long bookingId, String note) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = bookingRepository.findLockedById(bookingId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));
        cancelLockedByGym(booking, note, now, true);
    }

    /** Locks and cancels each booking that still holds a slot and hasn't started; skips the rest. */
    private GymCancellations cancelAllByGym(Collection<Long> bookingIds, String note, LocalDateTime now, boolean tellTrainer) {
        int cancelled = 0;
        int refunded = 0;
        for (Long id : bookingIds) {
            Booking booking = bookingRepository.findLockedById(id).orElse(null);
            if (booking == null || !booking.holdsSlotAt(now) || !booking.getStartsAt().isAfter(now)) {
                continue;   // started already, or overdue and about to expire
            }
            if (cancelLockedByGym(booking, note, now, tellTrainer)) {
                refunded++;
            }
            cancelled++;
        }
        return new GymCancellations(cancelled, refunded);
    }

    /**
     * Cancels a locked booking on the gym's side and tells the member. A paid booking is refunded in
     * full, and the refund email doubles as the cancellation notice. Returns true if it was refunded.
     */
    private boolean cancelLockedByGym(Booking booking, String note, LocalDateTime now, boolean tellTrainer) {
        boolean wasPaid = booking.statusAt(now) == BookingStatus.PAID;
        booking.cancelByGym(note, now);
        if (wasPaid) {
            paymentService.refundCancelledBooking(booking, RefundReason.GYM_CANCELLED, now);
        } else {
            notificationSender.send(booking.getMember().getEmail(), "Your session was cancelled",
                    "We're sorry, but the gym had to cancel your session with " + booking.getTrainer().getFullName()
                            + " on " + BookingTexts.when(booking) + "."
                            + (booking.getCancellationNote() == null ? "" : "\nReason: " + booking.getCancellationNote())
                            + "\nNothing was charged. You can book another time in the app.");
        }
        if (tellTrainer) {
            notificationSender.send(booking.getTrainer().getEmail(), "Session cancelled by the gym",
                    "The gym cancelled your session with " + booking.getMember().getFullName()
                            + " on " + BookingTexts.when(booking) + "."
                            + (booking.getCancellationNote() == null ? "" : "\nReason: " + booking.getCancellationNote())
                            + (wasPaid ? "\nThe member was refunded in full." : ""));
        }
        return wasPaid;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> pendingRequests(Long trainerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return bookingRepository.findByTrainerIdAndStatusOrderByRespondByAtAsc(trainerId, BookingStatus.REQUESTED)
                .stream()
                .filter(b -> b.holdsSlotAt(now))              // overdue but not yet marked EXPIRED
                .map(b -> BookingResponse.from(b, now))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> upcomingSchedule(Long trainerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return bookingRepository
                .findByTrainerIdAndStatusInAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
                        trainerId, SCHEDULED, now.toLocalDate())
                .stream()
                .filter(b -> b.holdsSlotAt(now) && b.getEndsAt().isAfter(now))   // drops unpaid ones past their deadline
                .map(b -> BookingResponse.from(b, now))
                .toList();
    }

    @Override
    @Transactional
    public BookingResponse accept(Long trainerId, Long bookingId, String message) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = trainerBooking(trainerId, bookingId);

        LocalDateTime deadline = now.plusHours(paymentWindowHours);
        LocalDateTime payBy = deadline.isBefore(booking.getStartsAt()) ? deadline : booking.getStartsAt();

        booking.accept(message, now, payBy);

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

    /**
     * Deliberately not @Transactional: each booking is expired in its own short transaction that locks
     * only that row, so one contended booking can't block or roll back the others.
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

    /** Locks the booking so a payment confirmed concurrently takes precedence over expiry. */
    private boolean expireOne(Long bookingId, LocalDateTime now) {
        Booking booking = bookingRepository.findLockedById(bookingId).orElse(null);
        if (booking == null || !booking.expireIfOverdue(now)) {
            return false;   // paid or cancelled in the meantime
        }
        String trainer = booking.getTrainer().getFullName();
        if (booking.getRespondedAt() == null) {
            notificationSender.send(booking.getMember().getEmail(), "Your session request expired",
                    trainer + " didn't answer your request for " + BookingTexts.when(booking)
                            + " in time, so it was cancelled. Please pick another time in the app.");
        } else {
            notificationSender.send(booking.getMember().getEmail(), "Your booking expired",
                    "Your session with " + trainer + " on " + BookingTexts.when(booking)
                            + " wasn't paid in time, so the time was released. Nothing was charged.");
            notificationSender.send(booking.getTrainer().getEmail(), "Unpaid session released",
                    booking.getMember().getFullName() + " didn't pay for the session on " + BookingTexts.when(booking)
                            + ", so that time is free again.");
        }
        return true;
    }

    private Booking memberBooking(Long memberId, Long bookingId) {
        return bookingRepository.findByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));
    }

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
