package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.BlockImpactResponse;
import com.mycompany.gymbooking.dto.BlockedTimeCreatedResponse;
import com.mycompany.gymbooking.dto.BlockedTimeRequest;
import com.mycompany.gymbooking.dto.BlockedTimeResponse;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.BlockedTime;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.BookingStatus;
import com.mycompany.gymbooking.model.Branch;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.repository.BlockedTimeRepository;
import com.mycompany.gymbooking.repository.BookingRepository;
import com.mycompany.gymbooking.repository.BranchRepository;
import com.mycompany.gymbooking.repository.TrainerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockedTimeServiceImpl implements BlockedTimeService {

    private static final Logger log = LoggerFactory.getLogger(BlockedTimeServiceImpl.class);
    private static final long MAX_DAYS = 366;

    private final BlockedTimeRepository blockedTimeRepository;
    private final BranchRepository branchRepository;
    private final TrainerRepository trainerRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final Clock clock;

    public BlockedTimeServiceImpl(BlockedTimeRepository blockedTimeRepository,
                                  BranchRepository branchRepository,
                                  TrainerRepository trainerRepository,
                                  BookingRepository bookingRepository,
                                  BookingService bookingService,
                                  Clock clock) {
        this.blockedTimeRepository = blockedTimeRepository;
        this.branchRepository = branchRepository;
        this.trainerRepository = trainerRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockedTimeResponse> upcoming() {
        return blockedTimeRepository.findByEndDateGreaterThanEqualOrderByStartDateAscStartTimeAsc(LocalDate.now(clock))
                .stream()
                .map(BlockedTimeResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BlockImpactResponse preview(BlockedTimeRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        BlockedTime block = build(request, now);
        List<Booking> affected = bookingRepository.findForBlock(trainerId(block), branchId(block),
                        Booking.SLOT_HOLDING, block.getStartDate(), block.getEndDate())
                .stream()
                .filter(b -> b.holdsSlotAt(now) && b.getStartsAt().isAfter(now))
                .filter(b -> block.blocks(b.getDate(), b.getStartTime(), b.getEndTime()))
                .toList();
        int paid = (int) affected.stream().filter(b -> b.statusAt(now) == BookingStatus.PAID).count();
        return new BlockImpactResponse(affected.size(), paid);
    }

    @Override
    @Transactional
    public BlockedTimeCreatedResponse create(BlockedTimeRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        BlockedTime block = blockedTimeRepository.save(build(request, now));

        // Ids only: BookingService locks each booking and re-checks it before cancelling.
        List<Long> inside = bookingRepository.findSlotsForBlock(trainerId(block), branchId(block),
                        Booking.SLOT_HOLDING, block.getStartDate(), block.getEndDate())
                .stream()
                .filter(slot -> block.blocks(slot.getDate(), slot.getStartTime(), slot.getEndTime()))
                .map(BookingRepository.BookingSlot::getId)
                .toList();
        GymCancellations result = bookingService.cancelByGym(inside, noteFor(block));
        if (result.cancelled() > 0) {
            log.info("Blocked time {} cancelled {} booking(s) ({} refunded)", block.getId(), result.cancelled(), result.refunded());
        }
        return new BlockedTimeCreatedResponse(BlockedTimeResponse.from(block), result.cancelled(), result.refunded());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        BlockedTime block = blockedTimeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("BLOCK_NOT_FOUND", "This blocked time was already removed."));
        blockedTimeRepository.delete(block);
    }

    private BlockedTime build(BlockedTimeRequest request, LocalDateTime now) {
        if (request.branchId() == null && request.trainerId() == null) {
            throw new BadRequestException("INVALID_BLOCK", "Pick a branch or a trainer.");
        }
        if (request.branchId() != null && request.trainerId() != null) {
            throw new BadRequestException("INVALID_BLOCK", "Pick either a branch or a trainer, not both.");
        }
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (end.isBefore(start)) {
            throw new BadRequestException("INVALID_DATES", "The end date is before the start date.");
        }
        if (start.isBefore(now.toLocalDate())) {
            throw new BadRequestException("INVALID_DATES", "The start date is in the past.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BadRequestException("INVALID_DATES", "A blocked time can be at most a year long.");
        }
        if ((request.startTime() == null) != (request.endTime() == null)) {
            throw new BadRequestException("INVALID_TIMES",
                    "Set both a start and an end time, or neither to block whole days.");
        }
        if (request.startTime() != null && !request.endTime().isAfter(request.startTime())) {
            throw new BadRequestException("INVALID_TIMES", "The end time must be after the start time.");
        }

        if (request.branchId() != null) {
            Branch branch = branchRepository.findById(request.branchId())
                    .orElseThrow(() -> new BadRequestException("BRANCH_NOT_FOUND", "No branch with id " + request.branchId()));
            return BlockedTime.forBranch(branch, start, end, request.startTime(), request.endTime(), request.reason(), now);
        }
        Trainer trainer = trainerRepository.findById(request.trainerId())
                .orElseThrow(() -> new BadRequestException("TRAINER_NOT_FOUND", "No trainer with id " + request.trainerId()));
        return BlockedTime.forTrainer(trainer, start, end, request.startTime(), request.endTime(), request.reason(), now);
    }

    /** Shown to members whose sessions the block cancels. */
    private static String noteFor(BlockedTime block) {
        if (block.getReason() != null) {
            return block.getReason();
        }
        return block.getBranch() != null
                ? "The branch is closed at that time."
                : "Your trainer isn't available at that time.";
    }

    private static Long trainerId(BlockedTime block) {
        return block.getTrainer() == null ? null : block.getTrainer().getId();
    }

    private static Long branchId(BlockedTime block) {
        return block.getBranch() == null ? null : block.getBranch().getId();
    }
}
