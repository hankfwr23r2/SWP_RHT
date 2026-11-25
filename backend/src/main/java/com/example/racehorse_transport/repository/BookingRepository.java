package com.example.racehorse_transport.repository;

import org.springframework.data.domain.Pageable;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
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

    @EntityGraph(attributePaths = {"customer", "departure", "departure.route"})
    List<Booking> findByStatusInOrderByCreatedAtDescIdDesc(Collection<BookingStatus> statuses, Pageable page);

    interface HorseCount {
        Long getBookingId();
        Long getHorseCount();
    }

    @Query("""
            SELECT h.booking.id AS bookingId, COUNT(h) AS horseCount FROM BookingHorse h
            WHERE h.booking.id IN :bookingIds
                AND h.status = com.example.racehorse_transport.entity.enums.BookingHorseStatus.ACTIVE
            GROUP BY h.booking.id""")
    List<HorseCount> countActiveHorses(@Param("bookingIds") Collection<Long> bookingIds);

    Optional<Booking> findByCode(String code);

    // Tra cứu công khai bằng số vận đơn
    Optional<Booking> findByWaybillNumber(String waybillNumber);

    @EntityGraph(attributePaths = "departure")
    List<Booking> findByCustomer_User_UsernameOrderByCreatedAtDescIdDesc(String username);

    @EntityGraph(attributePaths = "departure")
    Optional<Booking> findByIdAndCustomer_User_Username(Long id, String username);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.booking.id = :bookingId
              AND p.paymentType = com.example.racehorse_transport.entity.enums.PaymentType.DEPOSIT
              AND p.status = com.example.racehorse_transport.entity.enums.PaymentStatus.PAID
            """)
    java.math.BigDecimal paidDepositForStabling(Long bookingId);

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
            SELECT b FROM Booking b LEFT JOIN FETCH b.departure d
            WHERE b.status = :status AND (d.id IS NOT NULL OR b.bookingType = :directType)
            ORDER BY d.plannedDepartureAt, b.requestedPickupDate, b.id
            """)
    List<Booking> findForDocumentCheck(BookingStatus status, BookingType directType);

    @Query("""
            SELECT b.id FROM Booking b LEFT JOIN b.departure d
            WHERE b.status = :status AND (d.plannedDepartureAt BETWEEN :now AND :cutoff
                OR (d.id IS NULL AND b.bookingType = :directType AND b.requestedPickupDate BETWEEN :today AND :lastDate))
            ORDER BY b.id
            """)
    List<Long> findIdsForDocumentReminder(BookingStatus status, Instant now, Instant cutoff,
            BookingType directType, java.time.LocalDate today, java.time.LocalDate lastDate);

    @Query("""
            SELECT count(n) > 0 FROM Notification n
            WHERE n.booking.id = :bookingId AND n.title = :title AND n.createdAt >= :start AND n.createdAt < :end
            """)
    boolean hasNotificationBetween(Long bookingId, String title, Instant start, Instant end);

    @Query("SELECT min(n.createdAt) FROM Notification n WHERE n.booking.id = :bookingId AND n.title = :title AND n.createdAt >= :start")
    Optional<Instant> firstNoticeSince(Long bookingId, String title, Instant start);

    @Query("""
            SELECT count(a) FROM AuditLog a
            WHERE a.targetTable = 'booking' AND a.targetId = :bookingId AND a.createdAt >= :since
                AND a.action LIKE concat(:actionPrefix, '%')
            """)
    long countBalanceHoldContactsSince(Long bookingId, String actionPrefix, Instant since);

    @Query("""
            SELECT MIN(s.balanceHoldAt) FROM HorseStay s
            WHERE s.bookingHorse.booking.id = :bookingId AND s.actualEndAt IS NULL
            """)
    Optional<Instant> findActiveBalanceHoldAt(Long bookingId);

    @Query("""
            SELECT count(p) > 0 FROM Payment p
            WHERE p.booking.id = :bookingId
                AND p.status = com.example.racehorse_transport.entity.enums.PaymentStatus.PENDING
                AND (p.paymentType = com.example.racehorse_transport.entity.enums.PaymentType.BALANCE
                    OR EXISTS (SELECT c.id FROM CostItem c WHERE c.extraChargePayment = p
                        AND c.horseStay.balanceHoldAt IS NOT NULL AND c.horseStay.actualEndAt IS NOT NULL))
            """)
    boolean hasOutstandingPickupReturnPayments(Long bookingId);

    @Query("""
            SELECT count(s) > 0 FROM HorseStay s WHERE s.bookingHorse.booking.id = :bookingId
                AND s.balanceHoldAt IS NOT NULL AND s.actualEndAt > s.balanceHoldAt
                AND EXISTS (SELECT h.id FROM Handover h WHERE h.horseStay = s
                    AND h.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.RETURN
                    AND h.status = com.example.racehorse_transport.entity.enums.HandoverStatus.DONE)
                AND NOT EXISTS (SELECT a.id FROM AuditLog a WHERE a.targetTable = 'horse_stay'
                    AND a.targetId = s.id AND a.action = 'BALANCE_HOLD_BILLED')
            """)
    boolean hasUnbilledHoldingReturn(Long bookingId);

    @Query("""
            SELECT MAX(h.handedAt) FROM Handover h
            WHERE h.horseStay.bookingHorse.booking.id = :bookingId AND h.horseStay.balanceHoldAt IS NOT NULL
                AND h.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.RETURN
                AND h.status = com.example.racehorse_transport.entity.enums.HandoverStatus.DONE
                AND NOT EXISTS (SELECT s.id FROM HorseStay s WHERE s.bookingHorse.booking.id = :bookingId
                    AND s.balanceHoldAt IS NOT NULL AND s.actualEndAt IS NULL)
            """)
    Optional<Instant> findReturnedBalanceHoldAt(Long bookingId);

    @Query("""
            SELECT DISTINCT s.bookingHorse.booking.id FROM HorseStay s
            WHERE s.balanceHoldAt IS NOT NULL AND s.actualEndAt <= :cutoff
                AND s.bookingHorse.booking.status = com.example.racehorse_transport.entity.enums.BookingStatus.SCHEDULE_CHANGE_REQUIRED
            ORDER BY s.bookingHorse.booking.id
            """)
    List<Long> findReturnedBalanceHoldIds(Instant cutoff);

    @Query("""
            SELECT h.booking.id FROM BookingStatusHistory h WHERE h.reason = :reason
                AND h.booking.status = h.toStatus AND h.changedAt <= :cutoff
                AND NOT EXISTS (SELECT n.id FROM BookingStatusHistory n
                    WHERE n.booking = h.booking AND n.id > h.id)
            ORDER BY h.booking.id
            """)
    List<Long> findScheduleChangesByReason(String reason, Instant cutoff);

    @Query("""
            SELECT COUNT(h) > 0 FROM BookingStatusHistory h
            WHERE h.booking.id = :bookingId AND h.booking.status = h.toStatus AND h.reason = :reason
                AND NOT EXISTS (SELECT n.id FROM BookingStatusHistory n WHERE n.booking = h.booking AND n.id > h.id)
            """)
    boolean hasLatestStatusReason(Long bookingId, String reason);

    @Query("""
            SELECT DISTINCT l.bookingHorse.booking.id FROM HorseLeg l
            WHERE l.status = com.example.racehorse_transport.entity.enums.HorseLegStatus.IN_PROGRESS
                AND l.boardTripStop.stopType = com.example.racehorse_transport.entity.enums.TripStopType.PICKUP
                AND l.boardTripStop.actualDepartureAt IS NULL
                AND l.bookingHorse.booking.status = com.example.racehorse_transport.entity.enums.BookingStatus.CONFIRMED
                AND EXISTS (SELECT p.id FROM Payment p WHERE p.booking = l.bookingHorse.booking
                    AND p.paymentType = com.example.racehorse_transport.entity.enums.PaymentType.BALANCE
                    AND p.status = com.example.racehorse_transport.entity.enums.PaymentStatus.PENDING)
            ORDER BY l.bookingHorse.booking.id
            """)
    List<Long> findWaitingPickupBalanceIds();

    @Query("""
            SELECT MIN(l.actualStartAt) FROM HorseLeg l
            WHERE l.bookingHorse.booking.id = :bookingId
                AND l.status = com.example.racehorse_transport.entity.enums.HorseLegStatus.IN_PROGRESS
                AND l.boardTripStop.stopType = com.example.racehorse_transport.entity.enums.TripStopType.PICKUP
                AND l.boardTripStop.actualDepartureAt IS NULL
            """)
    Optional<Instant> findWaitingPickupSince(Long bookingId);

    @Query("""
            SELECT count(hh) > 0 FROM HandoverHorse hh JOIN hh.handover h JOIN hh.horseLeg l
            WHERE l.bookingHorse.booking.id = :bookingId
                AND hh.microchipMatched = true
                AND l.status <> com.example.racehorse_transport.entity.enums.HorseLegStatus.CANCELLED
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
