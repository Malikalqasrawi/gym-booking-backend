package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.BookingStatus;
import com.mycompany.gymbooking.model.CancelledBy;
import com.mycompany.gymbooking.model.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Booking view for members and trainers. {@code status}, {@code canPay} and {@code canCancel} are
 * evaluated at the current time so the app doesn't duplicate the booking rules. Contact details
 * are intentionally excluded.
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
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime payBy,
        boolean canPay,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime cancelUntil,  // null if not cancellable
        boolean canCancel,
        PaymentInfo payment,                                                    // null until paid
        CancelledBy cancelledBy,                                                // null unless cancelled
        String cancellationNote
) {

    /** Without payment details, for trainer views. */
    public static BookingResponse from(Booking booking, LocalDateTime now) {
        return from(booking, null, now);
    }

    /** With payment details, for the booking's member. {@code payment} may be null. */
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
                PaymentInfo.from(payment),
                booking.getCancelledBy(),
                booking.getCancellationNote()
        );
    }
}
