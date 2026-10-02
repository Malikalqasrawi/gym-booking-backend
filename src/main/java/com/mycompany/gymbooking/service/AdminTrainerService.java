package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AdminTrainerResponse;
import com.mycompany.gymbooking.dto.ScheduleRequest;
import com.mycompany.gymbooking.dto.TrainerDeactivationResponse;
import com.mycompany.gymbooking.dto.TrainerRequest;
import java.util.List;

/** Trainer management for admins: invites, profiles, weekly schedules and deactivation. */
public interface AdminTrainerService {

    /** All trainers, including invited and deactivated ones. */
    List<AdminTrainerResponse> list();

    AdminTrainerResponse get(Long trainerId);

    /** Creates the trainer without a usable password and emails them an invite code. */
    AdminTrainerResponse create(TrainerRequest request);

    /** Changing the email of an invited trainer sends the invite to the new address. */
    AdminTrainerResponse update(Long trainerId, TrainerRequest request);

    /** Replaces the weekly schedule. Existing bookings are kept. */
    AdminTrainerResponse updateSchedule(Long trainerId, ScheduleRequest request);

    /** Sends a new invite code, replacing the previous one. */
    AdminTrainerResponse resendInvite(Long trainerId);

    /**
     * Hides the trainer from members, blocks their login and cancels their upcoming bookings,
     * refunding paid ones in full.
     */
    TrainerDeactivationResponse deactivate(Long trainerId, String reason);

    AdminTrainerResponse reactivate(Long trainerId);
}
