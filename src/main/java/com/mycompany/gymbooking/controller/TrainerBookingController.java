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

/**
 * Trainer endpoints (SecurityConfig: only role TRAINER can call /api/trainer/**).
 *
 *   GET  /api/trainer/requests               requests waiting for MY answer, most urgent first
 *   GET  /api/trainer/schedule               MY accepted sessions that haven't finished yet
 *   POST /api/trainer/requests/{id}/accept   body (optional): { "message": "See you there!" }
 *   POST /api/trainer/requests/{id}/reject   body (optional): { "message": "I'm away that day" }
 *
 * A request sent to another trainer → 404, as if it didn't exist.
 */
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

    /** required = false: the app may send no body at all when there's no message. */
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
