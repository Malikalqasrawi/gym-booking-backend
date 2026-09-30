package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mycompany.gymbooking.model.Branch;
import java.time.LocalTime;

public record BranchResponse(
        Long id,
        String name,
        String address,
        String city,
        double latitude,
        double longitude,
        String phone,
        @JsonFormat(pattern = "HH:mm") LocalTime openingTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closingTime
) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getCity(),
                branch.getLatitude(),
                branch.getLongitude(),
                branch.getPhone(),
                branch.getOpeningTime(),
                branch.getClosingTime()
        );
    }
}
