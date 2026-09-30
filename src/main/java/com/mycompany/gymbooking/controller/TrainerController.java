package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.AvailabilityResponse;
import com.mycompany.gymbooking.dto.TrainerFilter;
import com.mycompany.gymbooking.dto.TrainerResponse;
import com.mycompany.gymbooking.model.Gender;
import com.mycompany.gymbooking.model.TrainingCategory;
import com.mycompany.gymbooking.service.AvailabilityService;
import com.mycompany.gymbooking.service.TrainerService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoints members use while choosing a session (any logged-in user):
 *
 *   GET /api/branches/{branchId}/trainers                       → trainers at that branch
 *   GET /api/trainers/{id}                                      → one trainer + weekly schedule
 *   GET /api/trainers/{id}/availability?date=2026-10-04&duration=60
 *                                                               → free start times that day
 *
 * PATH variables  (/trainers/2)        identify WHICH thing.
 * QUERY parameters (?date=...&duration=60) are options for HOW to read it.
 */
@RestController
@RequestMapping("/api")
public class TrainerController {

    private final TrainerService trainerService;
    private final AvailabilityService availabilityService;

    public TrainerController(TrainerService trainerService, AvailabilityService availabilityService) {
        this.trainerService = trainerService;
        this.availabilityService = availabilityService;
    }

    /**
     * Optional filters, e.g. /api/branches/1/trainers?category=YOGA&gender=FEMALE&maxRate=20
     * Spring turns "YOGA" into TrainingCategory.YOGA; an unknown value → 400 INVALID_PARAMETER.
     */
    @GetMapping("/branches/{branchId}/trainers")
    public List<TrainerResponse> trainersAtBranch(@PathVariable Long branchId,
                                                  @RequestParam(required = false) TrainingCategory category,
                                                  @RequestParam(required = false) Gender gender,
                                                  @RequestParam(required = false) BigDecimal maxRate) {
        return trainerService.findByBranch(branchId, new TrainerFilter(category, gender, maxRate));
    }

    @GetMapping("/trainers/{id}")
    public TrainerResponse getTrainer(@PathVariable Long id) {
        return trainerService.findById(id);
    }

    /**
     * @DateTimeFormat(ISO.DATE) → the date must look like 2026-10-04 (year-month-day).
     * defaultValue = "60"      → if the app doesn't send ?duration=..., use 60 minutes.
     */
    @GetMapping("/trainers/{id}/availability")
    public AvailabilityResponse availability(@PathVariable Long id,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             @RequestParam(defaultValue = "60") int duration) {
        return availabilityService.getAvailability(id, date, duration);
    }
}
