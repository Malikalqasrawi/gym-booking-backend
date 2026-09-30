package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.BookingRequest;
import com.mycompany.gymbooking.dto.BookingResponse;
import com.mycompany.gymbooking.security.SecurityUser;
import com.mycompany.gymbooking.service.BookingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Member booking endpoints. The member id always comes from the token, never from the request. */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> requestSession(@AuthenticationPrincipal SecurityUser me,
                                                          @Valid @RequestBody BookingRequest request) {
        BookingResponse created = bookingService.requestSession(me.getUser().getId(), request);
        return ResponseEntity.created(URI.create("/api/bookings/" + created.id())).body(created);
    }

    @GetMapping("/mine")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal SecurityUser me) {
        return bookingService.myBookings(me.getUser().getId());
    }

    @GetMapping("/{id}")
    public BookingResponse myBooking(@AuthenticationPrincipal SecurityUser me, @PathVariable Long id) {
        return bookingService.myBooking(me.getUser().getId(), id);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal SecurityUser me, @PathVariable Long id) {
        return bookingService.cancel(me.getUser().getId(), id);
    }
}
