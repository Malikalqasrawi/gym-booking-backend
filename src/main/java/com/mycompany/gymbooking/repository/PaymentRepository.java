package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.Payment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);

    List<Payment> findByBookingIdIn(Collection<Long> bookingIds);

    /** Returns only the booking id so the caller can lock the booking before loading the payment. */
    @Query("select p.booking.id from Payment p where p.providerPaymentId = :providerPaymentId")
    Optional<Long> findBookingIdByProviderPaymentId(@Param("providerPaymentId") String providerPaymentId);
}
