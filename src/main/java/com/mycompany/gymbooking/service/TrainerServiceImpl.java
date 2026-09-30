package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.TrainerFilter;
import com.mycompany.gymbooking.dto.TrainerResponse;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.repository.BranchRepository;
import com.mycompany.gymbooking.repository.TrainerRepository;
import com.mycompany.gymbooking.repository.WorkingHoursRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainerServiceImpl implements TrainerService {

    private final TrainerRepository trainerRepository;
    private final BranchRepository branchRepository;
    private final WorkingHoursRepository workingHoursRepository;

    public TrainerServiceImpl(TrainerRepository trainerRepository,
                              BranchRepository branchRepository,
                              WorkingHoursRepository workingHoursRepository) {
        this.trainerRepository = trainerRepository;
        this.branchRepository = branchRepository;
        this.workingHoursRepository = workingHoursRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainerResponse> findByBranch(Long branchId, TrainerFilter filter) {
        // A missing branch is an error (404), not just "no trainers"
        if (!branchRepository.existsById(branchId)) {
            throw new NotFoundException("BRANCH_NOT_FOUND", "No branch with id " + branchId);
        }
        // A branch has only a handful of trainers, so filtering in Java is simple and fast enough.
        // (With thousands of rows we'd filter in the SQL query instead.)
        return trainerRepository.findByBranchIdOrderByFullNameAsc(branchId).stream()
                .filter(filter::matches)
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TrainerResponse findById(Long trainerId) {
        Trainer trainer = trainerRepository.findById(trainerId)
                .orElseThrow(() -> new NotFoundException("TRAINER_NOT_FOUND", "No trainer with id " + trainerId));
        return toResponse(trainer);
    }

    /**
     * Entity → DTO, with the trainer's weekly schedule.
     * This runs INSIDE the @Transactional method, so reading trainer.getBranch() (LAZY) is allowed.
     */
    private TrainerResponse toResponse(Trainer trainer) {
        return TrainerResponse.from(trainer, workingHoursRepository.findByTrainerId(trainer.getId()));
    }
}
