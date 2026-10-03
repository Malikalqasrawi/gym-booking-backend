package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.PhoneVerification;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    /** SMS codes sent on this day by the whole gym. */
    @Query("select coalesce(sum(v.sendsThatDay), 0) from PhoneVerification v where v.sendsDay = :day")
    long countSentOn(@Param("day") LocalDate day);
}
