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

/** Public trainer profile. Contact details are intentionally excluded. */
public record TrainerResponse(
        Long id,
        String fullName,
        String specialty,
        String bio,
        Integer yearsOfExperience,
        Long branchId,
        String branchName,
        BigDecimal hourlyRate,          // JOD per hour
        TrainingCategory category,
        Gender gender,
        String languages,
        List<String> tags,
        List<String> certifications,
        List<WorkingHoursResponse> schedule
) {

    /** Sunday first, as the week starts on Sunday in Jordan. */
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
                List.copyOf(trainer.getTags()),            // copy while the lazy collections can still be loaded
                List.copyOf(trainer.getCertifications()),
                hours.stream().sorted(WEEK_ORDER).map(WorkingHoursResponse::from).toList()
        );
    }

    private static int sundayFirst(DayOfWeek day) {
        return day.getValue() % 7;
    }
}
