package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mycompany.gymbooking.model.WorkingHours;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkingHoursResponse(
        DayOfWeek dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime
) {

    public static WorkingHoursResponse from(WorkingHours hours) {
        return new WorkingHoursResponse(hours.getDayOfWeek(), hours.getStartTime(), hours.getEndTime());
    }
}
