package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.BlockImpactResponse;
import com.mycompany.gymbooking.dto.BlockedTimeCreatedResponse;
import com.mycompany.gymbooking.dto.BlockedTimeRequest;
import com.mycompany.gymbooking.dto.BlockedTimeResponse;
import com.mycompany.gymbooking.service.BlockedTimeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Branch closures and trainers' time off. */
@RestController
@RequestMapping("/api/admin/blocked-times")
public class BlockedTimeController {

    private final BlockedTimeService blockedTimeService;

    public BlockedTimeController(BlockedTimeService blockedTimeService) {
        this.blockedTimeService = blockedTimeService;
    }

    @GetMapping
    public List<BlockedTimeResponse> upcoming() {
        return blockedTimeService.upcoming();
    }

    /** How many bookings the block would cancel, so the admin can confirm first. */
    @PostMapping("/preview")
    public BlockImpactResponse preview(@Valid @RequestBody BlockedTimeRequest request) {
        return blockedTimeService.preview(request);
    }

    @PostMapping
    public ResponseEntity<BlockedTimeCreatedResponse> create(@Valid @RequestBody BlockedTimeRequest request) {
        BlockedTimeCreatedResponse created = blockedTimeService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/blocked-times/" + created.blockedTime().id())).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        blockedTimeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
