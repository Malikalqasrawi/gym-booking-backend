package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.BlockedTime;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockedTimeRepository extends JpaRepository<BlockedTime, Long> {

    /** Blocks that haven't ended, soonest first. */
    @EntityGraph(attributePaths = {"branch", "trainer"})
    List<BlockedTime> findByEndDateGreaterThanEqualOrderByStartDateAscStartTimeAsc(LocalDate today);

    void deleteByBranchId(Long branchId);

    /** Blocks on {@code date} for the trainer or for their branch. */
    @Query("""
            select b from BlockedTime b
            left join b.trainer t
            left join b.branch br
            where b.startDate <= :date and b.endDate >= :date
              and (t.id = :trainerId or br.id = :branchId)
            """)
    List<BlockedTime> findOnDate(@Param("trainerId") Long trainerId,
                                 @Param("branchId") Long branchId,
                                 @Param("date") LocalDate date);
}
