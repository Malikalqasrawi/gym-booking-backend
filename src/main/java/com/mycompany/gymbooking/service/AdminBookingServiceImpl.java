package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AdminBookingResponse;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.BookingStatus;
import com.mycompany.gymbooking.model.Payment;
import com.mycompany.gymbooking.repository.BookingRepository;
import com.mycompany.gymbooking.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBookingServiceImpl implements AdminBookingService {

    private static final int PAST_LIMIT = 200;

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final Clock clock;

    public AdminBookingServiceImpl(BookingRepository bookingRepository,
                                   PaymentRepository paymentRepository,
                                   BookingService bookingService,
                                   Clock clock) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminBookingResponse> list(boolean upcoming, Long branchId, Long trainerId, BookingStatus status) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> bookings = upcoming
                ? bookingRepository.findUpcomingForAdmin(now.toLocalDate(), now.toLocalTime(), branchId, trainerId)
                : bookingRepository.findPastForAdmin(now.toLocalDate(), now.toLocalTime(), branchId, trainerId,
                        PageRequest.of(0, PAST_LIMIT));
        if (status != null) {
            bookings = bookings.stream().filter(b -> b.statusAt(now) == status).toList();
        }

        List<Long> ids = bookings.stream().map(Booking::getId).toList();
        Map<Long, Payment> paymentByBooking = paymentRepository.findByBookingIdIn(ids).stream()
                .collect(Collectors.toMap(payment -> payment.getBooking().getId(), Function.identity()));

        return bookings.stream()
                .map(b -> AdminBookingResponse.from(b, paymentByBooking.get(b.getId()), now))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminBookingResponse get(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));
        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        return AdminBookingResponse.from(booking, payment, LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public AdminBookingResponse cancel(Long bookingId, String reason) {
        bookingService.cancelByGym(bookingId, reason);
        return get(bookingId);
    }
}
