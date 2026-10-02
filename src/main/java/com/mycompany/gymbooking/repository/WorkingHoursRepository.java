package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.WorkingHours;
import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findByTrainerId(Long trainerId);

    List<WorkingHours> findByTrainerIdIn(Collection<Long> trainerIds);

    void deleteByTrainerId(Long trainerId);

    List<WorkingHours> findByTrainerIdAndDayOfWeek(Long trainerId, DayOfWeek dayOfWeek);
}
