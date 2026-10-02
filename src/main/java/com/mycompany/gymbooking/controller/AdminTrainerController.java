package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.AdminTrainerResponse;
import com.mycompany.gymbooking.dto.DeactivateTrainerRequest;
import com.mycompany.gymbooking.dto.ScheduleRequest;
import com.mycompany.gymbooking.dto.TrainerDeactivationResponse;
import com.mycompany.gymbooking.dto.TrainerRequest;
import com.mycompany.gymbooking.service.AdminTrainerService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trainer management. /api/admin/** is restricted to admins in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/trainers")
public class AdminTrainerController {

    private final AdminTrainerService adminTrainerService;

    public AdminTrainerController(AdminTrainerService adminTrainerService) {
        this.adminTrainerService = adminTrainerService;
    }

    @GetMapping
    public List<AdminTrainerResponse> list() {
        return adminTrainerService.list();
    }

    @GetMapping("/{id}")
    public AdminTrainerResponse get(@PathVariable Long id) {
        return adminTrainerService.get(id);
    }

    @PostMapping
    public ResponseEntity<AdminTrainerResponse> create(@Valid @RequestBody TrainerRequest request) {
        AdminTrainerResponse created = adminTrainerService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/trainers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public AdminTrainerResponse update(@PathVariable Long id, @Valid @RequestBody TrainerRequest request) {
        return adminTrainerService.update(id, request);
    }

    @PutMapping("/{id}/schedule")
    public AdminTrainerResponse updateSchedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request) {
        return adminTrainerService.updateSchedule(id, request);
    }

    @PostMapping("/{id}/invite")
    public AdminTrainerResponse resendInvite(@PathVariable Long id) {
        return adminTrainerService.resendInvite(id);
    }

    @PostMapping("/{id}/deactivate")
    public TrainerDeactivationResponse deactivate(@PathVariable Long id,
                                                  @Valid @RequestBody(required = false) DeactivateTrainerRequest request) {
        return adminTrainerService.deactivate(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/reactivate")
    public AdminTrainerResponse reactivate(@PathVariable Long id) {
        return adminTrainerService.reactivate(id);
    }
}
