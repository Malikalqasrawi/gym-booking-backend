package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.BranchRequest;
import com.mycompany.gymbooking.dto.BranchResponse;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.exception.ConflictException;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.Branch;
import com.mycompany.gymbooking.repository.BlockedTimeRepository;
import com.mycompany.gymbooking.repository.BookingRepository;
import com.mycompany.gymbooking.repository.BranchRepository;
import com.mycompany.gymbooking.repository.TrainerRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Branch CRUD. Names are unique (case-insensitive) and closing time must be after opening time. */
@Service
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final TrainerRepository trainerRepository;
    private final BookingRepository bookingRepository;
    private final BlockedTimeRepository blockedTimeRepository;

    public BranchServiceImpl(BranchRepository branchRepository,
                             TrainerRepository trainerRepository,
                             BookingRepository bookingRepository,
                             BlockedTimeRepository blockedTimeRepository) {
        this.branchRepository = branchRepository;
        this.trainerRepository = trainerRepository;
        this.bookingRepository = bookingRepository;
        this.blockedTimeRepository = blockedTimeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> findAll(String city) {
        List<Branch> branches = (city == null || city.isBlank())
                ? branchRepository.findAllByOrderByNameAsc()
                : branchRepository.findAllByCityIgnoreCaseOrderByNameAsc(city.trim());

        return branches.stream()
                .map(BranchResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse findById(Long id) {
        return BranchResponse.from(getBranchOrThrow(id));
    }

    @Override
    @Transactional
    public BranchResponse create(BranchRequest request) {
        String name = request.name().trim();

        if (branchRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("BRANCH_NAME_TAKEN", "A branch named '" + name + "' already exists");
        }
        checkOpeningHours(request);

        Branch branch = new Branch(
                name,
                request.address().trim(),
                request.city().trim(),
                request.latitude(),
                request.longitude(),
                blankToNull(request.phone()),
                request.openingTime(),
                request.closingTime());

        Branch saved = branchRepository.save(branch);
        return BranchResponse.from(saved);
    }

    @Override
    @Transactional
    public BranchResponse update(Long id, BranchRequest request) {
        Branch branch = getBranchOrThrow(id);
        String name = request.name().trim();

        if (branchRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("BRANCH_NAME_TAKEN", "A branch named '" + name + "' already exists");
        }
        checkOpeningHours(request);

        branch.updateDetails(
                name,
                request.address().trim(),
                request.city().trim(),
                request.latitude(),
                request.longitude(),
                blankToNull(request.phone()),
                request.openingTime(),
                request.closingTime());

        return BranchResponse.from(branch);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Branch branch = getBranchOrThrow(id);
        // Bookings keep their branch for the booking history, so a branch that was ever used stays.
        if (trainerRepository.existsByBranchId(id) || bookingRepository.existsByBranchId(id)) {
            throw new ConflictException("BRANCH_IN_USE",
                    "This branch has trainers or bookings, so it can't be deleted.");
        }
        blockedTimeRepository.deleteByBranchId(id);
        branchRepository.delete(branch);
    }

    private Branch getBranchOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("BRANCH_NOT_FOUND", "No branch with id " + id));
    }

    private void checkOpeningHours(BranchRequest request) {
        if (!request.closingTime().isAfter(request.openingTime())) {
            throw new BadRequestException("INVALID_HOURS", "Closing time must be after opening time");
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
