package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Booking;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByCode(String code);

    @EntityGraph(attributePaths = "departure")
    List<Booking> findByCustomer_User_UsernameOrderByCreatedAtDescIdDesc(String username);

    @EntityGraph(attributePaths = "departure")
    Optional<Booking> findByIdAndCustomer_User_Username(Long id, String username);

    @Modifying
    @Query(value = """
            INSERT INTO booking (code, customer_id, booking_type, departure_id, requested_pickup_date,
                                 emergency_spending_limit, status, created_by, created_at)
            VALUES (:code, :customerId, :type, :departureId, :pickupDate, :spendingLimit, :status, :actorId, :now)
            ON CONFLICT ON CONSTRAINT uk_booking_code DO NOTHING
            """, nativeQuery = true)
    int insertIfCodeAvailable(String code, Long customerId, String type, Long departureId,
            LocalDate pickupDate, BigDecimal spendingLimit, String status, Long actorId, Instant now);

    // SELECT ... FOR UPDATE: hai thao tác đổi trạng thái cùng một đơn chạy lần lượt
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findForUpdate(@Param("id") Long id);

    List<Booking> findByDeparture_Id(Long departureId);
}
