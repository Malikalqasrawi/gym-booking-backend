package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AvailabilityResponse;
import java.time.LocalDate;

/** Answers: "when can I book this trainer on this date, for a session of this length?" */
public interface AvailabilityService {

    AvailabilityResponse getAvailability(Long trainerId, LocalDate date, int durationMinutes);
}
