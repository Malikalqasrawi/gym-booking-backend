package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mycompany.gymbooking.model.WorkingHours;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

public record WorkingHoursResponse(
        DayOfWeek dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime
) {

    /** Sunday first, as the week starts on Sunday in Jordan. */
    private static final Comparator<WorkingHours> WEEK_ORDER =
            Comparator.comparingInt((WorkingHours h) -> h.getDayOfWeek().getValue() % 7)
                      .thenComparing(WorkingHours::getStartTime);

    public static WorkingHoursResponse from(WorkingHours hours) {
        return new WorkingHoursResponse(hours.getDayOfWeek(), hours.getStartTime(), hours.getEndTime());
    }

    /** The blocks in week order. */
    public static List<WorkingHoursResponse> week(List<WorkingHours> hours) {
        return hours.stream().sorted(WEEK_ORDER).map(WorkingHoursResponse::from).toList();
    }
}
