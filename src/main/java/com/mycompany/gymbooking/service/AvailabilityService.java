package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AvailabilityResponse;
import java.time.LocalDate;

/** Computes the bookable start times for a trainer on a date for a given session length. */
public interface AvailabilityService {

    AvailabilityResponse getAvailability(Long trainerId, LocalDate date, int durationMinutes);
}
