package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.PhoneVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {
}
