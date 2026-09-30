package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.Trainer;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * Trainers only. Trainers are stored in the "users" table (user_type = 'TRAINER'),
 * and this repository automatically adds that condition to every query.
 *
 *   findByBranchIdOrderByFullNameAsc(2)
 *     → SELECT * FROM users WHERE user_type = 'TRAINER' AND branch_id = 2 ORDER BY full_name
 *
 * "BranchId" means: follow the "branch" field, then use its "id".
 */
public interface TrainerRepository extends JpaRepository<Trainer, Long> {

    List<Trainer> findByBranchIdOrderByFullNameAsc(Long branchId);

    /**
     * Same as findById, but LOCKS the trainer's row until our transaction ends
     * (SQL: SELECT ... FOR UPDATE). A second request for the same trainer waits at this line
     * until the first one has saved its booking, so two members can't grab the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Trainer> findLockedById(Long id);
}
