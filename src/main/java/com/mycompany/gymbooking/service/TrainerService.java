package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.TrainerFilter;
import com.mycompany.gymbooking.dto.TrainerResponse;
import java.util.List;

/** Read-only access to bookable trainers, as members see them. */
public interface TrainerService {

    /** Trainers at the branch that match the filter. Throws NotFoundException if the branch doesn't exist. */
    List<TrainerResponse> findByBranch(Long branchId, TrainerFilter filter);

    /** Trainers at every branch that match the filter, e.g. all yoga trainers. */
    List<TrainerResponse> findAll(TrainerFilter filter);

    /** A trainer with their weekly schedule. Throws NotFoundException if not found. */
    TrainerResponse findById(Long trainerId);
}
