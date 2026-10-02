package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.AdminBookingResponse;
import com.mycompany.gymbooking.dto.CancelByGymRequest;
import com.mycompany.gymbooking.model.BookingStatus;
import com.mycompany.gymbooking.service.AdminBookingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;

    public AdminBookingController(AdminBookingService adminBookingService) {
        this.adminBookingService = adminBookingService;
    }

    /** {@code ?past=true} lists sessions that have ended instead of upcoming ones. */
    @GetMapping
    public List<AdminBookingResponse> list(@RequestParam(defaultValue = "false") boolean past,
                                           @RequestParam(required = false) Long branchId,
                                           @RequestParam(required = false) Long trainerId,
                                           @RequestParam(required = false) BookingStatus status) {
        return adminBookingService.list(!past, branchId, trainerId, status);
    }

    @GetMapping("/{id}")
    public AdminBookingResponse get(@PathVariable Long id) {
        return adminBookingService.get(id);
    }

    @PostMapping("/{id}/cancel")
    public AdminBookingResponse cancel(@PathVariable Long id,
                                       @Valid @RequestBody(required = false) CancelByGymRequest request) {
        return adminBookingService.cancel(id, request == null ? null : request.reason());
    }
}
