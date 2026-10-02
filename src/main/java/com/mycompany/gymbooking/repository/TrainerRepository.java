package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.Trainer;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TrainerRepository extends JpaRepository<Trainer, Long> {

    List<Trainer> findByBranchIdOrderByFullNameAsc(Long branchId);

    boolean existsByBranchId(Long branchId);

    /** All trainers with their branch, tags and certifications loaded in one query. */
    @EntityGraph(attributePaths = {"branch", "tags", "certifications"})
    List<Trainer> findAllByOrderByFullNameAsc();

    /**
     * Locks the trainer row (SELECT ... FOR UPDATE) for the rest of the transaction, serializing
     * concurrent bookings for the same trainer so two members can't take the same slot.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Trainer> findLockedById(Long id);
}
