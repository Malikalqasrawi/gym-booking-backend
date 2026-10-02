package com.mycompany.gymbooking.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Trainer account status and when members may book them. */
class TrainerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

    private static Trainer completeTrainer() {
        Trainer trainer = new Trainer("Rami Khalil", "rami@test.com", "0791234567", "hash", "Boxing", "Bio", 8);
        trainer.assignToBranch(new Branch("Khalda Branch", "Wasfi Al-Tal Street", "Amman", 32.0, 35.83,
                "065000002", LocalTime.of(6, 0), LocalTime.of(22, 0)));
        trainer.changeHourlyRate(new BigDecimal("25"));
        trainer.updateProfile(TrainingCategory.BOXING, Gender.MALE, "Arabic", List.of(), List.of());
        return trainer;
    }

    @Test
    @DisplayName("invited → active after joining → deactivated → active again")
    void statusLifecycle() {
        Trainer trainer = completeTrainer();
        assertEquals(TrainerStatus.INVITED, trainer.getStatus());
        assertFalse(trainer.isBookable(), "hasn't joined yet");

        trainer.markVerified();
        assertEquals(TrainerStatus.ACTIVE, trainer.getStatus());
        assertTrue(trainer.isBookable());

        trainer.deactivate(NOW);
        assertEquals(TrainerStatus.DEACTIVATED, trainer.getStatus());
        assertFalse(trainer.isActive());
        assertFalse(trainer.isBookable());

        trainer.reactivate();
        assertEquals(TrainerStatus.ACTIVE, trainer.getStatus());
        assertTrue(trainer.isBookable());
    }

    @Test
    @DisplayName("an active trainer needs a branch, a rate and a profile to be bookable")
    void incompleteProfileIsNotBookable() {
        Trainer trainer = new Trainer("Rami Khalil", "rami@test.com", "0791234567", "hash", "Boxing", "Bio", 8);
        trainer.markVerified();
        assertFalse(trainer.isBookable());
        trainer.changeHourlyRate(new BigDecimal("25"));
        trainer.updateProfile(TrainingCategory.BOXING, Gender.MALE, "Arabic", List.of(), List.of());
        assertFalse(trainer.isBookable(), "no branch yet");
        assertFalse(completeTrainer().isBookable(), "a complete profile alone isn't enough without joining");
    }
}
