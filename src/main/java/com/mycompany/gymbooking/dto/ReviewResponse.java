package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.mycompany.gymbooking.model.Review;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A review as profiles show it; the admin also gets the full name and whether it is hidden. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewResponse(
        Long id,
        Long bookingId,
        Long trainerId,
        String trainerName,
        String memberName,
        int rating,
        String comment,
        LocalDate sessionDate,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime createdAt,
        String reply,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime repliedAt,
        Boolean hidden,          // admin only
        String hiddenReason      // admin only
) {

    /** For profiles and the trainer: the member is shown as e.g. "Malik Q.". */
    public static ReviewResponse publicView(Review review) {
        return of(review, shortName(review.getMember().getFullName()), null, null);
    }

    public static ReviewResponse adminView(Review review) {
        return of(review, review.getMember().getFullName(), review.isHidden(), review.getHiddenReason());
    }

    private static ReviewResponse of(Review review, String memberName, Boolean hidden, String hiddenReason) {
        return new ReviewResponse(
                review.getId(),
                review.getBooking().getId(),
                review.getTrainer().getId(),
                review.getTrainer().getFullName(),
                memberName,
                review.getRating(),
                review.getComment(),
                review.getBooking().getDate(),
                review.getCreatedAt(),
                review.getTrainerReply(),
                review.getRepliedAt(),
                hidden,
                hiddenReason);
    }

    /** "Malik Al-Qasrawi" -> "Malik A." */
    static String shortName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
