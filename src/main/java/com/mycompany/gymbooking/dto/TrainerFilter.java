package com.mycompany.gymbooking.dto;

import com.mycompany.gymbooking.model.Gender;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.model.TrainingCategory;
import java.math.BigDecimal;

/**
 * The optional filters from the URL:
 *   GET /api/branches/1/trainers?category=YOGA&gender=FEMALE&maxRate=20
 *
 * Any filter left out (null) means "don't filter on that".
 */
public record TrainerFilter(TrainingCategory category, Gender gender, BigDecimal maxRate) {

    /** Does this trainer pass ALL the filters that were given? */
    public boolean matches(Trainer trainer) {
        if (category != null && category != trainer.getCategory()) {
            return false;
        }
        if (gender != null && gender != trainer.getGender()) {
            return false;
        }
        if (maxRate != null && (!trainer.hasHourlyRate() || trainer.getHourlyRate().compareTo(maxRate) > 0)) {
            return false;   // compareTo, not >, because BigDecimal is an object (20.000 vs 20 compare as equal)
        }
        return true;
    }
}
