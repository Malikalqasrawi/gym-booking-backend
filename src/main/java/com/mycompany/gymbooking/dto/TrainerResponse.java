package com.mycompany.gymbooking.dto;

import com.mycompany.gymbooking.model.Branch;
import com.mycompany.gymbooking.model.Gender;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.model.TrainingCategory;
import com.mycompany.gymbooking.model.WorkingHours;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

/**
 * What the app gets for a trainer:
 * {
 *   "id": 2, "fullName": "Sara Haddad", "specialty": "Strength & conditioning",
 *   "bio": "...", "yearsOfExperience": 6,
 *   "branchId": 1, "branchName": "Abdoun Branch",
 *   "schedule": [ { "dayOfWeek": "SUNDAY", "startTime": "08:00", "endTime": "16:00" }, ... ]
 * }
 * No email, phone or password: members don't need them.
 */
public record TrainerResponse(
        Long id,
        String fullName,
        String specialty,
        String bio,
        Integer yearsOfExperience,
        Long branchId,
        String branchName,
        BigDecimal hourlyRate,          // JOD per hour; the app multiplies by the duration to show the price
        TrainingCategory category,      // e.g. "YOGA" (the app shows its own label + icon)
        Gender gender,
        String languages,               // "Arabic, English"
        List<String> tags,              // ["Beginners", "Weight loss"]
        List<String> certifications,
        List<WorkingHoursResponse> schedule
) {

    /** Jordan's week starts on Sunday, so the schedule is sorted SUNDAY → SATURDAY. */
    private static final Comparator<WorkingHours> WEEK_ORDER =
            Comparator.comparingInt((WorkingHours h) -> sundayFirst(h.getDayOfWeek()))
                      .thenComparing(WorkingHours::getStartTime);

    public static TrainerResponse from(Trainer trainer, List<WorkingHours> hours) {
        Branch branch = trainer.getBranch();
        return new TrainerResponse(
                trainer.getId(),
                trainer.getFullName(),
                trainer.getSpecialty(),
                trainer.getBio(),
                trainer.getYearsOfExperience(),
                branch == null ? null : branch.getId(),
                branch == null ? null : branch.getName(),
                trainer.getHourlyRate(),
                trainer.getCategory(),
                trainer.getGender(),
                trainer.getLanguages(),
                List.copyOf(trainer.getTags()),            // copy now, while the database session is still open
                List.copyOf(trainer.getCertifications()),
                hours.stream().sorted(WEEK_ORDER).map(WorkingHoursResponse::from).toList()
        );
    }

    /** SUNDAY → 0, MONDAY → 1 ... SATURDAY → 6  (Java's own order starts at MONDAY = 1). */
    private static int sundayFirst(DayOfWeek day) {
        return day.getValue() % 7;
    }
}
