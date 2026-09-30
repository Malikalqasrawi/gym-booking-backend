package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

/** One free time the member can pick: { "start": "09:30", "end": "10:30" } */
public record TimeSlotResponse(
        @JsonFormat(pattern = "HH:mm") LocalTime start,
        @JsonFormat(pattern = "HH:mm") LocalTime end
) {
}
