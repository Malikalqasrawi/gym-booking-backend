package com.mycompany.gymbooking.model;

/**
 * The main kind of training a trainer does. Used by the "filter by specialty" chips in the app:
 *   GET /api/branches/1/trainers?category=YOGA
 *
 * Each value carries its own label (an enum can have fields and a constructor, like a class).
 */
public enum TrainingCategory {
    STRENGTH("Strength"),
    HIIT("HIIT & cardio"),
    YOGA("Yoga"),
    PILATES("Pilates"),
    BOXING("Boxing"),
    CROSSFIT("CrossFit"),
    REHAB("Rehab & mobility");

    private final String label;

    TrainingCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
