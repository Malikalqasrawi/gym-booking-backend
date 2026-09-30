package com.mycompany.gymbooking.model;

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
