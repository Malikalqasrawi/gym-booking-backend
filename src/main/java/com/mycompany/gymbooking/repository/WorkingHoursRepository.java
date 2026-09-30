package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.WorkingHours;
import java.time.DayOfWeek;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *   findByTrainerId(1)
 *     → SELECT * FROM working_hours WHERE trainer_id = 1
 *
 *   findByTrainerIdAndDayOfWeek(1, SUNDAY)
 *     → SELECT * FROM working_hours WHERE trainer_id = 1 AND day_of_week = 'SUNDAY'
 */
public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findByTrainerId(Long trainerId);

    List<WorkingHours> findByTrainerIdAndDayOfWeek(Long trainerId, DayOfWeek dayOfWeek);
}
