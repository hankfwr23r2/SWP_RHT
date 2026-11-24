package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBooking_IdOrderById(Long bookingId);

    @Query("select p.booking.id from Payment p where p.id = :paymentId")
    Optional<Long> findBookingId(Long paymentId);
}
