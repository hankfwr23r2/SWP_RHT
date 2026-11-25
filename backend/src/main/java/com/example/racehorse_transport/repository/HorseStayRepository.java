package com.example.racehorse_transport.repository;

import java.time.Instant;
import jakarta.persistence.LockModeType;

import com.example.racehorse_transport.entity.enums.HorseStayType;

import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Handover;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

public interface HorseStayRepository extends JpaRepository<HorseStay, Long> {

    @Query("""
            SELECT DISTINCT s.bookingHorse.booking.id FROM HorseStay s
            WHERE s.actualStartAt IS NOT NULL AND s.actualEndAt IS NULL
                AND (s.stayType = com.example.racehorse_transport.entity.enums.HorseStayType.STABLING OR
                    (s.stayType = com.example.racehorse_transport.entity.enums.HorseStayType.ASSEMBLY
                     AND s.bookingHorse.booking.status = com.example.racehorse_transport.entity.enums.BookingStatus.CONFIRMED))
            ORDER BY s.bookingHorse.booking.id
            """)
    List<Long> findBalanceHoldCandidates();

    @Query("""
            SELECT s.id FROM HorseStay s WHERE s.location.id = :locationId AND s.actualStartAt IS NOT NULL AND s.actualEndAt IS NULL
                AND s.stayType = com.example.racehorse_transport.entity.enums.HorseStayType.ASSEMBLY
                AND s.bookingHorse.booking.status = com.example.racehorse_transport.entity.enums.BookingStatus.CONFIRMED
                AND EXISTS (SELECT p.id FROM Payment p WHERE p.booking = s.bookingHorse.booking
                    AND p.paymentType = com.example.racehorse_transport.entity.enums.PaymentType.BALANCE
                    AND p.status = com.example.racehorse_transport.entity.enums.PaymentStatus.PENDING)
            """)
    List<Long> findUnpaidAssemblyStayIds(Long locationId);

    @Query("""
            SELECT DISTINCT h.horseStay.bookingHorse.booking.id FROM Handover h
            WHERE h.horseStay.balanceHoldAt IS NOT NULL AND h.horseStay.actualEndAt IS NOT NULL
                AND h.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.RETURN
                AND h.status = com.example.racehorse_transport.entity.enums.HandoverStatus.DONE
                AND NOT EXISTS (SELECT a.id FROM AuditLog a WHERE a.targetTable = 'horse_stay'
                    AND a.targetId = h.horseStay.id AND a.action = 'BALANCE_HOLD_BILLED')
            ORDER BY h.horseStay.bookingHorse.booking.id
            """)
    List<Long> findUnbilledReturnedBalanceHoldIds();

    @Query("""
            SELECT h FROM Handover h JOIN FETCH h.horseStay s JOIN FETCH h.handledBy
            WHERE s.bookingHorse.booking.id = :bookingId AND s.balanceHoldAt IS NOT NULL AND s.actualEndAt IS NOT NULL
                AND h.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.RETURN
                AND h.status = com.example.racehorse_transport.entity.enums.HandoverStatus.DONE
                AND NOT EXISTS (SELECT a.id FROM AuditLog a WHERE a.targetTable = 'horse_stay'
                    AND a.targetId = s.id AND a.action = 'BALANCE_HOLD_BILLED')
            ORDER BY h.id
            """)
    List<Handover> findUnbilledBalanceHoldReturns(Long bookingId);

    List<HorseStay> findByReadyAtIsNotNullAndActualEndAtIsNull();

    @Query("""
            SELECT s FROM HorseStay s WHERE s.balanceHoldAt IS NOT NULL
                AND EXISTS (SELECT l.id FROM HorseLeg l WHERE l.bookingHorse = s.bookingHorse
                    AND l.boardTripStop.id = :tripStopId AND l.actualStartAt = s.actualEndAt
                    AND l.legType = com.example.racehorse_transport.entity.enums.HorseLegType.LINEHAUL)
            ORDER BY s.bookingHorse.booking.id, s.id
            """)
    List<HorseStay> findPlannedStablingForDeparture(Long tripStopId);

    @Query("""
            SELECT CASE WHEN COUNT(l) > 0 THEN TRUE ELSE FALSE END FROM HorseLeg l
            WHERE l.bookingHorse.id = :horseId AND l.actualStartAt = :at
                AND l.legType = com.example.racehorse_transport.entity.enums.HorseLegType.LINEHAUL
            """)
    boolean hasLinehaulBoarding(Long horseId, Instant at);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM HorseStay s WHERE s.id = :stayId")
    Optional<HorseStay> findForUpdate(Long stayId);

    @Query("SELECT s.bookingHorse.booking.id FROM HorseStay s WHERE s.id = :stayId")
    Optional<Long> findBookingId(Long stayId);

    // Lưu trú đang diễn ra của một con (đã vào, chưa ra)
    Optional<HorseStay> findFirstByBookingHorse_IdAndActualStartAtIsNotNullAndActualEndAtIsNull(Long bookingHorseId);

    @EntityGraph(attributePaths = {"bookingHorse", "bookingHorse.horse", "bookingHorse.booking", "location"})
    List<HorseStay> findByLocation_IdAndActualStartAtIsNotNullAndActualEndAtIsNullOrderByActualStartAt(Long locationId);

    // Theo dõi hành trình: mọi lưu trú của các con trong một đơn
    @EntityGraph(attributePaths = {"location", "operatorParty"})
    List<HorseStay> findByLocation_IdAndActualStartAtIsNullOrderByPlannedStartAt(Long locationId);

    List<HorseStay> findByBookingHorse_IdInOrderById(Collection<Long> bookingHorseIds);

    @EntityGraph(attributePaths = {"location"})
    List<HorseStay> findByBookingHorse_Booking_Id(Long bookingId);

    // Gửi chuồng đang diễn ra đã quá hạn dự kiến
    @EntityGraph(attributePaths = {"bookingHorse", "bookingHorse.horse", "bookingHorse.booking", "bookingHorse.booking.customer", "location"})
    List<HorseStay> findByStayTypeAndActualStartAtIsNotNullAndActualEndAtIsNullAndPlannedEndAtBefore(HorseStayType stayType, Instant at);

    // Lịch hẹn chưa bắt đầu của một con (khách tự chở tới hub)
    long deleteByBookingHorse_Booking_IdAndStayTypeAndActualStartAtIsNull(Long bookingId, HorseStayType type);

    void deleteByBookingHorse_IdAndActualStartAtIsNull(Long bookingHorseId);
}
