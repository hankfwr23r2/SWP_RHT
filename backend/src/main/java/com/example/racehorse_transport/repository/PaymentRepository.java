package com.example.racehorse_transport.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.Payment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBooking_IdOrderById(Long bookingId);

    @EntityGraph(attributePaths = {"booking", "booking.customer", "refundedPayment"})
    List<Payment> findByPaymentTypeAndStatusOrderById(PaymentType type, PaymentStatus status);

    @Query("""
            SELECT DISTINCT p.booking.customer.userId FROM Payment p
            WHERE p.paymentType = :type AND p.status = :status AND p.dueAt < :now
            ORDER BY p.booking.customer.userId
            """)
    List<Long> findCustomersWithOverdue(PaymentType type, PaymentStatus status, Instant now);

    @Query("""
            SELECT count(p) > 0 FROM Payment p WHERE p.booking.customer.userId = :customerId
            AND p.paymentType = :type AND p.status = :status AND p.dueAt < :now
            """)
    boolean hasOverdue(Long customerId, PaymentType type, PaymentStatus status, Instant now);

    @Query("select p.booking.id from Payment p where p.id = :paymentId")
    Optional<Long> findBookingId(Long paymentId);
}
