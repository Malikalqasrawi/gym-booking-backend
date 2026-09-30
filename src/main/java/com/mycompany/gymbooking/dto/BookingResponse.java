package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.BookingStatus;
import com.mycompany.gymbooking.model.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A booking as the app sees it (used by both the member's and the trainer's screens).
 *
 * `status` is the status RIGHT NOW (an unanswered request past its deadline shows as EXPIRED),
 * and `canPay` / `canCancel` are worked out here, so the app doesn't have to copy the rules.
 * Only names are included, never emails or phone numbers.
 */
public record BookingResponse(
        Long id,
        BookingStatus status,
        Long trainerId,
        String trainerName,
        Long memberId,
        String memberName,
        Long branchId,
        String branchName,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        int durationMinutes,
        BigDecimal price,
        String memberNote,
        String trainerReply,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime respondBy,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime respondedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime payBy,        // ACCEPTED: pay before this
        boolean canPay,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime cancelUntil,  // null = can't be cancelled
        boolean canCancel,
        PaymentInfo payment                                                     // null until paid
) {

    /** Without payment details (trainer screens). */
    public static BookingResponse from(Booking booking, LocalDateTime now) {
        return from(booking, null, now);
    }

    /** With the receipt (the member's own screens). `payment` may be null. */
    public static BookingResponse from(Booking booking, Payment payment, LocalDateTime now) {
        boolean accepted = booking.statusAt(now) == BookingStatus.ACCEPTED;
        return new BookingResponse(
                booking.getId(),
                booking.statusAt(now),
                booking.getTrainer().getId(),
                booking.getTrainer().getFullName(),
                booking.getMember().getId(),
                booking.getMember().getFullName(),
                booking.getBranch().getId(),
                booking.getBranch().getName(),
                booking.getDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getDurationMinutes(),
                booking.getPrice(),
                booking.getMemberNote(),
                booking.getTrainerReply(),
                booking.getCreatedAt(),
                booking.getRespondByAt(),
                booking.getRespondedAt(),
                accepted ? booking.payDeadline() : null,
                booking.canBePaidAt(now),
                booking.cancelDeadline(now),
                booking.canBeCancelledAt(now),
                PaymentInfo.from(payment)
        );
    }
}
