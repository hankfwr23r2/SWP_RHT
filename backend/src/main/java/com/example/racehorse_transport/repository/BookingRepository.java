package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DepartureStatus;

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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = "departure")
    List<Booking> findByStatusInOrderByCreatedAtAscIdAsc(
            Collection<BookingStatus> statuses);

    Optional<Booking> findByCode(String code);

    // Tra cứu công khai bằng số vận đơn
    Optional<Booking> findByWaybillNumber(String waybillNumber);

    @EntityGraph(attributePaths = "departure")
    List<Booking> findByCustomer_User_UsernameOrderByCreatedAtDescIdDesc(String username);

    @EntityGraph(attributePaths = "departure")
    Optional<Booking> findByIdAndCustomer_User_Username(Long id, String username);

    @Modifying
    @Query(value = """
            INSERT INTO booking (code, customer_id, booking_type, departure_id, requested_pickup_date,
                                 emergency_spending_limit, status, created_by, created_at, pickup_method, delivery_method)
            VALUES (:code, :customerId, :type, :departureId, :pickupDate, :spendingLimit, :status, :actorId, :now,
                    :pickupMethod, :deliveryMethod)
            ON CONFLICT ON CONSTRAINT uk_booking_code DO NOTHING
            """, nativeQuery = true)
    int insertIfCodeAvailable(String code, Long customerId, String type, Long departureId,
            LocalDate pickupDate, BigDecimal spendingLimit, String status, Long actorId, Instant now,
            String pickupMethod, String deliveryMethod);

    // SELECT ... FOR UPDATE: hai thao tác đổi trạng thái cùng một đơn chạy lần lượt
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findForUpdate(@Param("id") Long id);

    List<Booking> findByDeparture_Id(Long departureId);

    @Query("SELECT b.id FROM Booking b WHERE b.departure.id = :departureId ORDER BY b.id")
    List<Long> findIdsByDeparture(@Param("departureId") Long departureId);

    @Query("""
            SELECT b.id FROM Booking b JOIN b.departure d
            WHERE b.status IN :statuses AND d.status IN :departureStatuses AND d.plannedDepartureAt <= :threshold
            ORDER BY b.id""")
    List<Long> findIdsForDecisionRelease(@Param("statuses") Collection<BookingStatus> statuses,
                                         @Param("departureStatuses") Collection<DepartureStatus> departureStatuses,
                                         @Param("threshold") Instant threshold);

    @Query("""
            SELECT b FROM Booking b JOIN FETCH b.departure d
            WHERE b.status = :status ORDER BY d.plannedDepartureAt, b.id
            """)
    List<Booking> findWithDeparture(BookingStatus status);

    @Query("""
            SELECT b.id FROM Booking b JOIN b.departure d
            WHERE b.status = :status AND d.plannedDepartureAt BETWEEN :now AND :cutoff ORDER BY b.id
            """)
    List<Long> findIdsForDocumentReminder(BookingStatus status, Instant now, Instant cutoff);

    @Query("""
            SELECT count(n) > 0 FROM Notification n
            WHERE n.booking.id = :bookingId AND n.title = :title AND n.createdAt >= :start AND n.createdAt < :end
            """)
    boolean hasNotificationBetween(Long bookingId, String title, Instant start, Instant end);

    @Query("""
            SELECT count(hh) > 0 FROM HandoverHorse hh JOIN hh.handover h JOIN hh.horseLeg l
            WHERE l.bookingHorse.booking.id = :bookingId
                AND h.status = com.example.racehorse_transport.entity.enums.HandoverStatus.DONE
                AND (h.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.PICKUP
                     OR (h.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.AGENT
                         AND h.toParty IS NULL AND l.bookingHorse.booking.bookingType =
                             com.example.racehorse_transport.entity.enums.BookingType.IMPORT))
            """)
    boolean hasReceivedHorses(Long bookingId);

    interface DepartureBookingCount {
        Long getDepartureId();
        Long getBookingCount();
    }

    @Query("""
            SELECT b.departure.id AS departureId, COUNT(b) AS bookingCount FROM Booking b
            WHERE b.departure.id IN :departureIds AND b.status IN :statuses GROUP BY b.departure.id""")
    List<DepartureBookingCount> countByDeparture(@Param("departureIds") Collection<Long> departureIds,
                                                 @Param("statuses") Collection<BookingStatus> statuses);
}
