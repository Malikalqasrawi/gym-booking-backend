package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mycompany.gymbooking.model.BlockedTime;
import com.mycompany.gymbooking.model.Trainer;
import java.time.LocalDate;
import java.time.LocalTime;

/** Either the branch fields or the trainer fields are set, depending on what is blocked. */
public record BlockedTimeResponse(
        Long id,
        Long branchId,
        String branchName,
        Long trainerId,
        String trainerName,
        LocalDate startDate,
        LocalDate endDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        boolean allDay,
        String reason
) {

    public static BlockedTimeResponse from(BlockedTime block) {
        Trainer trainer = block.getTrainer();
        return new BlockedTimeResponse(
                block.getId(),
                block.getBranch() == null ? null : block.getBranch().getId(),
                block.getBranch() == null ? null : block.getBranch().getName(),
                trainer == null ? null : trainer.getId(),
                trainer == null ? null : trainer.getFullName(),
                block.getStartDate(),
                block.getEndDate(),
                block.getStartTime(),
                block.getEndTime(),
                block.isAllDay(),
                block.getReason());
    }
}
