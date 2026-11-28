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
    @Query("""
            SELECT p FROM Payment p
            WHERE p.paymentType = :type AND p.status = :status AND p.dueAt < :now
            AND p.booking.customer.userId <> :customerId
            AND (p.disputedAt IS NULL OR p.disputeResolvedAt IS NOT NULL)
            AND (
                (:phone IS NOT NULL AND :phone <> '' AND p.booking.customer.phone = :phone)
                OR EXISTS (
                    SELECT 1 FROM BookingHorse other, BookingHorse selected
                    WHERE other.booking.customer = p.booking.customer AND selected.booking.id = :bookingId
                    AND other.horse.microchip <> '' AND other.horse.microchip = selected.horse.microchip
                )
            )
            """)
    List<Payment> findLinkedOverdue(Long bookingId, Long customerId, String phone,
                                    PaymentType type, PaymentStatus status, Instant now);

    List<Payment> findByBooking_IdOrderById(Long bookingId);

    @EntityGraph(attributePaths = {"booking", "booking.customer", "refundedPayment"})
    List<Payment> findByPaymentTypeAndStatusOrderById(PaymentType type, PaymentStatus status);

    @Query("""
            SELECT DISTINCT p.booking.customer.userId FROM Payment p
            WHERE p.paymentType = :type AND p.status = :status AND p.dueAt < :now
            AND (p.disputedAt IS NULL OR p.disputeResolvedAt IS NOT NULL)
            AND (p.issuedAt IS NULL OR p.issuedAt > :badBefore)
            ORDER BY p.booking.customer.userId
            """)
    List<Long> findCustomersWithOverdue(PaymentType type, PaymentStatus status, Instant now, Instant badBefore);

    List<Payment> findByBooking_Customer_UserIdAndPaymentType(Long customerId, PaymentType type);

    @Query("SELECT p.id FROM Payment p WHERE p.paymentType = :type AND p.status = :status AND p.issuedAt <= :before ORDER BY p.id")
    List<Long> findIssuedBefore(PaymentType type, PaymentStatus status, Instant before);

    @Query("select p.booking.id from Payment p where p.id = :paymentId")
    Optional<Long> findBookingId(Long paymentId);
}
