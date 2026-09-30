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

    /** The payments of many bookings in ONE query (for "My bookings"), instead of one query per booking. */
    List<Payment> findByBookingIdIn(Collection<Long> bookingIds);

    /**
     * Only the booking id, for Stripe's messages ("payment pi_123 succeeded").
     * We then LOCK that booking and load the payment fresh, so we never act on an old copy.
     */
    @Query("select p.booking.id from Payment p where p.providerPaymentId = :providerPaymentId")
    Optional<Long> findBookingIdByProviderPaymentId(@Param("providerPaymentId") String providerPaymentId);
}
