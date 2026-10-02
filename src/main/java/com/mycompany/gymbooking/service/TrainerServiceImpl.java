package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.TrainerFilter;
import com.mycompany.gymbooking.dto.TrainerResponse;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.repository.BranchRepository;
import com.mycompany.gymbooking.repository.ReviewRepository;
import com.mycompany.gymbooking.repository.ReviewRepository.TrainerRating;
import com.mycompany.gymbooking.repository.TrainerRepository;
import com.mycompany.gymbooking.repository.WorkingHoursRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainerServiceImpl implements TrainerService {

    private final TrainerRepository trainerRepository;
    private final BranchRepository branchRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final ReviewRepository reviewRepository;

    public TrainerServiceImpl(TrainerRepository trainerRepository,
                              BranchRepository branchRepository,
                              WorkingHoursRepository workingHoursRepository,
                              ReviewRepository reviewRepository) {
        this.trainerRepository = trainerRepository;
        this.branchRepository = branchRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.reviewRepository = reviewRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainerResponse> findByBranch(Long branchId, TrainerFilter filter) {
        if (!branchRepository.existsById(branchId)) {
            throw new NotFoundException("BRANCH_NOT_FOUND", "No branch with id " + branchId);
        }
        // Branches have few trainers, so filtering in memory is fine.
        return toResponses(trainerRepository.findByBranchIdOrderByFullNameAsc(branchId).stream()
                .filter(Trainer::isBookable)
                .filter(filter::matches)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainerResponse> findAll(TrainerFilter filter) {
        // The whole gym has a few dozen trainers, so this is filtered in memory too.
        return toResponses(trainerRepository.findAllByOrderByFullNameAsc().stream()
                .filter(Trainer::isBookable)
                .filter(filter::matches)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TrainerResponse findById(Long trainerId) {
        Trainer trainer = trainerRepository.findById(trainerId)
                .filter(Trainer::isBookable)
                .orElseThrow(() -> new NotFoundException("TRAINER_NOT_FOUND",
                        "This trainer is no longer available. Please pick another one."));
        return toResponses(List.of(trainer)).get(0);
    }

    /** The ratings of all the trainers come from one query, not one per trainer. */
    private List<TrainerResponse> toResponses(List<Trainer> trainers) {
        if (trainers.isEmpty()) {
            return List.of();
        }
        Map<Long, TrainerRating> ratings = reviewRepository.ratingsFor(trainers.stream().map(Trainer::getId).toList())
                .stream()
                .collect(Collectors.toMap(TrainerRating::getTrainerId, Function.identity()));
        return trainers.stream()
                .map(trainer -> TrainerResponse.from(trainer, workingHoursRepository.findByTrainerId(trainer.getId()),
                        ratings.get(trainer.getId())))
                .toList();
    }
}
