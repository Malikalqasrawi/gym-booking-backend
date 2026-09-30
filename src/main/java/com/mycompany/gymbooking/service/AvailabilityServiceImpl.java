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
 * Works out the free start times for one trainer on one day.
 *
 * Example: Sara works SUNDAY 08:00–16:00 at Abdoun (open 06:00–23:00), member wants 60 minutes:
 *
 *   1. Her working block that day ............ 08:00 ─────────────── 16:00
 *   2. Cut to the branch's opening hours ...... 08:00 ─────────────── 16:00   (branch is open longer, no change)
 *   3. Step every 30 minutes, session must END by 16:00:
 *        08:00–09:00, 08:30–09:30, 09:00–10:00 ... 15:00–16:00   → 15 slots
 *   4. If the date is today: drop times less than 60 minutes from now.
 *   5. Drop times that overlap a session already booked, or requested and still waiting for an answer.
 *
 * Times are handled as "minutes since midnight" (08:30 → 510) so adding durations
 * can never wrap around midnight by accident.
 */
@Service
public class AvailabilityServiceImpl implements AvailabilityService {

    /** The session lengths a member can choose. */
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

        // ---- Rule 1: only the durations we offer ----
        if (!ALLOWED_DURATIONS.contains(durationMinutes)) {
            throw new BadRequestException("INVALID_DURATION", "Duration must be 30, 45, 60 or 90 minutes");
        }

        // ---- Rule 2: from today up to 14 days ahead ----
        LocalDate today = LocalDate.now(clock);
        LocalDate lastDay = today.plusDays(daysAhead - 1);
        if (date.isBefore(today) || date.isAfter(lastDay)) {
            throw new BadRequestException("DATE_OUT_OF_RANGE",
                    "Pick a date between " + today + " and " + lastDay);
        }

        // ---- Rule 3: the trainer must exist ----
        Trainer trainer = trainerRepository.findById(trainerId)
                .orElseThrow(() -> new NotFoundException("TRAINER_NOT_FOUND", "No trainer with id " + trainerId));

        Branch branch = trainer.getBranch();
        if (branch == null) {
            // Not assigned to a branch yet → nowhere to train → no free times
            return new AvailabilityResponse(trainer.getId(), trainer.getFullName(), null, null,
                    date, durationMinutes, List.of());
        }

        // ---- Earliest allowed start: today → now + 60 min, other days → midnight ----
        int earliestStart = date.equals(today)
                ? toMinutes(LocalTime.now(clock)) + minNoticeMinutes
                : 0;

        // Sessions that already take up time that day (accepted, or requested and not expired yet)
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> taken = bookingRepository
                .findByTrainerIdAndDateAndStatusIn(trainerId, date, Booking.SLOT_HOLDING)
                .stream()
                .filter(booking -> booking.holdsSlotAt(now))
                .toList();

        int branchOpen = toMinutes(branch.getOpeningTime());
        int branchClose = toMinutes(branch.getClosingTime());

        // TreeMap keeps the slots sorted by start time and ignores duplicates
        // (in case two working blocks overlap).
        TreeMap<Integer, TimeSlotResponse> slots = new TreeMap<>();

        for (WorkingHours block : workingHoursRepository.findByTrainerIdAndDayOfWeek(trainerId, date.getDayOfWeek())) {

            // Step 2: the trainer can only train while the branch is open
            int from = Math.max(toMinutes(block.getStartTime()), branchOpen);
            int to = Math.min(toMinutes(block.getEndTime()), branchClose);

            // Step 3: every 30 minutes, as long as the whole session fits before "to"
            for (int start = from; start + durationMinutes <= to; start += stepMinutes) {
                if (start < earliestStart) {
                    continue;                              // Step 4: too soon (today only)
                }
                LocalTime slotStart = toTime(start);
                LocalTime slotEnd = toTime(start + durationMinutes);
                if (taken.stream().anyMatch(booking -> booking.overlaps(slotStart, slotEnd))) {
                    continue;                              // Step 5: that time is already taken
                }
                slots.put(start, new TimeSlotResponse(slotStart, slotEnd));
            }
        }

        return new AvailabilityResponse(trainer.getId(), trainer.getFullName(), branch.getId(), branch.getName(),
                date, durationMinutes, List.copyOf(slots.values()));
    }

    /** 08:30 → 510 */
    private static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    /** 510 → 08:30 */
    private static LocalTime toTime(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }
}
