package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.OutgoingEmail;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutgoingEmailRepository extends JpaRepository<OutgoingEmail, Long> {

    /** Emails waiting to be sent whose next attempt may start, oldest first. */
    @Query("""
            select e.id from OutgoingEmail e
            where e.status = com.mycompany.gymbooking.model.OutgoingEmail.Status.PENDING and e.nextAttemptAt <= :now
            order by e.nextAttemptAt
            """)
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable limit);

    /** Housekeeping: sent and failed emails created before {@code cutoff}. */
    @Modifying
    @Query("""
            delete from OutgoingEmail e
            where e.status <> com.mycompany.gymbooking.model.OutgoingEmail.Status.PENDING and e.createdAt < :cutoff
            """)
    int deleteFinishedBefore(@Param("cutoff") LocalDateTime cutoff);
}
