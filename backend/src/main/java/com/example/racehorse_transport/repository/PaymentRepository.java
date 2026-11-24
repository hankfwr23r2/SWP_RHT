package com.example.racehorse_transport.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBooking_IdOrderById(Long bookingId);

    @EntityGraph(attributePaths = {"booking", "booking.customer", "refundedPayment"})
    List<Payment> findByPaymentTypeAndStatusOrderById(PaymentType type, PaymentStatus status);

    @Query("select p.booking.id from Payment p where p.id = :paymentId")
    Optional<Long> findBookingId(Long paymentId);
}
