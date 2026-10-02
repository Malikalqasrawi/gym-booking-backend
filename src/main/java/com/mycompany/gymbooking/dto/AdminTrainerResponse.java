package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mycompany.gymbooking.model.Branch;
import com.mycompany.gymbooking.model.Gender;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.model.TrainerStatus;
import com.mycompany.gymbooking.model.TrainingCategory;
import com.mycompany.gymbooking.model.WorkingHours;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Trainer as the admin sees it: the public profile plus contact details and account status.
 *
 * @param inviteExpiresAt   set only while the invite is pending
 * @param upcomingBookings  bookings that hold a slot and haven't started
 */
public record AdminTrainerResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        TrainerStatus status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime inviteExpiresAt,
        String specialty,
        String bio,
        Integer yearsOfExperience,
        Long branchId,
        String branchName,
        BigDecimal hourlyRate,
        TrainingCategory category,
        Gender gender,
        String languages,
        List<String> tags,
        List<String> certifications,
        List<WorkingHoursResponse> schedule,
        long upcomingBookings
) {

    public static AdminTrainerResponse from(Trainer trainer, List<WorkingHours> hours, long upcomingBookings) {
        Branch branch = trainer.getBranch();
        TrainerStatus status = trainer.getStatus();
        return new AdminTrainerResponse(
                trainer.getId(),
                trainer.getFullName(),
                trainer.getEmail(),
                trainer.getPhone(),
                status,
                status == TrainerStatus.INVITED ? trainer.getVerificationCodeExpiresAt() : null,
                trainer.getSpecialty(),
                trainer.getBio(),
                trainer.getYearsOfExperience(),
                branch == null ? null : branch.getId(),
                branch == null ? null : branch.getName(),
                trainer.getHourlyRate(),
                trainer.getCategory(),
                trainer.getGender(),
                trainer.getLanguages(),
                List.copyOf(trainer.getTags()),
                List.copyOf(trainer.getCertifications()),
                WorkingHoursResponse.week(hours),
                upcomingBookings
        );
    }
}
