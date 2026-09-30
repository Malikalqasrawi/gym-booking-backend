package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

public record TimeSlotResponse(
        @JsonFormat(pattern = "HH:mm") LocalTime start,
        @JsonFormat(pattern = "HH:mm") LocalTime end
) {
}
