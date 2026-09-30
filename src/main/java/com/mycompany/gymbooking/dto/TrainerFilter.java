package com.mycompany.gymbooking.dto;

import com.mycompany.gymbooking.model.Gender;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.model.TrainingCategory;
import java.math.BigDecimal;

/** Optional trainer list filters; null fields are ignored. */
public record TrainerFilter(TrainingCategory category, Gender gender, BigDecimal maxRate) {

    public boolean matches(Trainer trainer) {
        if (category != null && category != trainer.getCategory()) {
            return false;
        }
        if (gender != null && gender != trainer.getGender()) {
            return false;
        }
        if (maxRate != null && (!trainer.hasHourlyRate() || trainer.getHourlyRate().compareTo(maxRate) > 0)) {
            return false;
        }
        return true;
    }
}
