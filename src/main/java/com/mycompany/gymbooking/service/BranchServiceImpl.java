package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.BranchRequest;
import com.mycompany.gymbooking.dto.BranchResponse;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.exception.ConflictException;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.Branch;
import com.mycompany.gymbooking.repository.BranchRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rules for branches:
 *   1. Two branches can't have the same name.
 *   2. Closing time must be after opening time.
 *   3. Reading/updating/deleting a branch that doesn't exist → 404.
 */
@Service
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;

    public BranchServiceImpl(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    // ---------------- READ (all) ----------------
    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> findAll(String city) {
        List<Branch> branches = (city == null || city.isBlank())
                ? branchRepository.findAllByOrderByNameAsc()
                : branchRepository.findAllByCityIgnoreCaseOrderByNameAsc(city.trim());

        return branches.stream()
                .map(BranchResponse::from)   // Branch (database object) → BranchResponse (JSON object)
                .toList();
    }

    // ---------------- READ (one) ----------------
    @Override
    @Transactional(readOnly = true)
    public BranchResponse findById(Long id) {
        return BranchResponse.from(getBranchOrThrow(id));
    }

    // ---------------- CREATE ----------------
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

        Branch saved = branchRepository.save(branch);   // INSERT INTO branches ...
        return BranchResponse.from(saved);
    }

    // ---------------- UPDATE ----------------
    @Override
    @Transactional
    public BranchResponse update(Long id, BranchRequest request) {
        Branch branch = getBranchOrThrow(id);
        String name = request.name().trim();

        // Same name is fine if it's THIS branch; not fine if another branch already uses it
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

        // No save() call needed: inside a @Transactional method, Hibernate notices the object
        // changed and runs "UPDATE branches SET ... WHERE id = ?" automatically when the method ends.
        return BranchResponse.from(branch);
    }

    // ---------------- DELETE ----------------
    @Override
    @Transactional
    public void delete(Long id) {
        Branch branch = getBranchOrThrow(id);
        branchRepository.delete(branch);                 // DELETE FROM branches WHERE id = ?
    }

    // ---------------- helpers ----------------

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
