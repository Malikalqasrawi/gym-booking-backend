package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.TrainerFilter;
import com.mycompany.gymbooking.dto.TrainerResponse;
import java.util.List;

/** Reading trainers (creating/editing them is the admin's job in Stage 5). */
public interface TrainerService {

    /** Trainers who work at this branch and pass the filters. 404 if the branch doesn't exist. */
    List<TrainerResponse> findByBranch(Long branchId, TrainerFilter filter);

    /** One trainer with their weekly schedule. 404 if not found. */
    TrainerResponse findById(Long trainerId);
}
