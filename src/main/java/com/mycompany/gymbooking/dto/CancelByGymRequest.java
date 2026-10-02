package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.Size;

/** Optional reason, shown to the member in the app and in the cancellation email. */
public record CancelByGymRequest(
        @Size(max = 300, message = "Reason is too long") String reason
) {
}
