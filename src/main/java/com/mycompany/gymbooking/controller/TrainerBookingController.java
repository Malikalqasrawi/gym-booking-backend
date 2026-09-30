package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.BookingResponse;
import com.mycompany.gymbooking.dto.TrainerReplyRequest;
import com.mycompany.gymbooking.security.SecurityUser;
import com.mycompany.gymbooking.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trainer")
public class TrainerBookingController {

    private final BookingService bookingService;

    public TrainerBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/requests")
    public List<BookingResponse> pendingRequests(@AuthenticationPrincipal SecurityUser me) {
        return bookingService.pendingRequests(me.getUser().getId());
    }

    @GetMapping("/schedule")
    public List<BookingResponse> upcomingSchedule(@AuthenticationPrincipal SecurityUser me) {
        return bookingService.upcomingSchedule(me.getUser().getId());
    }

    @PostMapping("/requests/{id}/accept")
    public BookingResponse accept(@AuthenticationPrincipal SecurityUser me, @PathVariable Long id,
                                  @Valid @RequestBody(required = false) TrainerReplyRequest body) {
        return bookingService.accept(me.getUser().getId(), id, body == null ? null : body.message());
    }

    @PostMapping("/requests/{id}/reject")
    public BookingResponse reject(@AuthenticationPrincipal SecurityUser me, @PathVariable Long id,
                                  @Valid @RequestBody(required = false) TrainerReplyRequest body) {
        return bookingService.reject(me.getUser().getId(), id, body == null ? null : body.message());
    }
}
