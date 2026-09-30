package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AvailabilityResponse;
import com.mycompany.gymbooking.dto.TimeSlotResponse;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.Branch;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.model.WorkingHours;
import com.mycompany.gymbooking.repository.BookingRepository;
import com.mycompany.gymbooking.repository.TrainerRepository;
import com.mycompany.gymbooking.repository.WorkingHoursRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clips each working block to branch hours, steps through it, and drops slots inside the minimum
 * notice or overlapping a booking. Uses minutes since midnight so durations can't wrap past midnight.
 */
@Service
public class AvailabilityServiceImpl implements AvailabilityService {

    public static final Set<Integer> ALLOWED_DURATIONS = Set.of(30, 45, 60, 90);

    private final TrainerRepository trainerRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final int daysAhead;
    private final int stepMinutes;
    private final int minNoticeMinutes;

    public AvailabilityServiceImpl(TrainerRepository trainerRepository,
                                   WorkingHoursRepository workingHoursRepository,
                                   BookingRepository bookingRepository,
                                   Clock clock,
                                   @Value("${app.booking.days-ahead}") int daysAhead,
                                   @Value("${app.booking.slot-step-minutes}") int stepMinutes,
                                   @Value("${app.booking.min-notice-minutes}") int minNoticeMinutes) {
        this.trainerRepository = trainerRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        this.daysAhead = daysAhead;
        this.stepMinutes = stepMinutes;
        this.minNoticeMinutes = minNoticeMinutes;
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(Long trainerId, LocalDate date, int durationMinutes) {

        if (!ALLOWED_DURATIONS.contains(durationMinutes)) {
            throw new BadRequestException("INVALID_DURATION", "Duration must be 30, 45, 60 or 90 minutes");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate lastDay = today.plusDays(daysAhead - 1);
        if (date.isBefore(today) || date.isAfter(lastDay)) {
            throw new BadRequestException("DATE_OUT_OF_RANGE",
                    "Pick a date between " + today + " and " + lastDay);
        }

        Trainer trainer = trainerRepository.findById(trainerId)
                .orElseThrow(() -> new NotFoundException("TRAINER_NOT_FOUND", "No trainer with id " + trainerId));

        Branch branch = trainer.getBranch();
        if (branch == null) {
            return new AvailabilityResponse(trainer.getId(), trainer.getFullName(), null, null,
                    date, durationMinutes, List.of());
        }

        int earliestStart = date.equals(today)
                ? toMinutes(LocalTime.now(clock)) + minNoticeMinutes
                : 0;

        // holdsSlotAt also drops overdue bookings the expiry job hasn't marked EXPIRED yet.
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> taken = bookingRepository
                .findByTrainerIdAndDateAndStatusIn(trainerId, date, Booking.SLOT_HOLDING)
                .stream()
                .filter(booking -> booking.holdsSlotAt(now))
                .toList();

        int branchOpen = toMinutes(branch.getOpeningTime());
        int branchClose = toMinutes(branch.getClosingTime());

        // Keyed by start minute: keeps slots sorted and deduplicates overlapping working blocks.
        TreeMap<Integer, TimeSlotResponse> slots = new TreeMap<>();

        for (WorkingHours block : workingHoursRepository.findByTrainerIdAndDayOfWeek(trainerId, date.getDayOfWeek())) {

            int from = Math.max(toMinutes(block.getStartTime()), branchOpen);
            int to = Math.min(toMinutes(block.getEndTime()), branchClose);

            for (int start = from; start + durationMinutes <= to; start += stepMinutes) {
                if (start < earliestStart) {
                    continue;
                }
                LocalTime slotStart = toTime(start);
                LocalTime slotEnd = toTime(start + durationMinutes);
                if (taken.stream().anyMatch(booking -> booking.overlaps(slotStart, slotEnd))) {
                    continue;
                }
                slots.put(start, new TimeSlotResponse(slotStart, slotEnd));
            }
        }

        return new AvailabilityResponse(trainer.getId(), trainer.getFullName(), branch.getId(), branch.getName(),
                date, durationMinutes, List.copyOf(slots.values()));
    }

    private static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static LocalTime toTime(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }
}
