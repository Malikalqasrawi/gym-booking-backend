package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    /** Member sign-ups that were never verified and got their last code before {@code cutoff}. */
    @Query("select m from Member m where m.verified = false and m.verificationCodeSentAt < :cutoff")
    List<User> findUnverifiedMembersBefore(@Param("cutoff") LocalDateTime cutoff);
}
