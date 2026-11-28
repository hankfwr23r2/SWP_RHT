package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingHorseRepository extends JpaRepository<BookingHorse, Long> {

    @Query("""
            SELECT h.id FROM BookingHorse h WHERE h.pickupChoiceDueAt <= :now
                AND h.pickupMissChoice IS NULL AND h.booking.status IN :statuses ORDER BY h.id
            """)
    List<Long> findExpiredPickupChoices(Instant now, Collection<BookingStatus> statuses);

    @Query("""
            SELECT h.id FROM BookingHorse h WHERE h.booking.status IN :statuses
                AND h.booking.departure.plannedDepartureAt <= :departureCutoff
                AND h.pickupMissChoice IN (com.example.racehorse_transport.entity.enums.PickupMissChoice.RETRY_PICKUP,
                                           com.example.racehorse_transport.entity.enums.PickupMissChoice.SELF_DELIVERY)
                AND NOT EXISTS (SELECT hh.id FROM HandoverHorse hh WHERE hh.horseLeg.bookingHorse = h
                    AND hh.microchipMatched = true AND hh.handover.handedAt >= h.pickupMissedAt
                    AND hh.handover.status = com.example.racehorse_transport.entity.enums.HandoverStatus.DONE
                    AND hh.handover.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.PICKUP)
            ORDER BY h.id
            """)
    List<Long> findPickupChoicesPastCheckIn(Instant departureCutoff, Collection<BookingStatus> statuses);

    @Query("""
            SELECT count(hh) > 0 FROM HandoverHorse hh WHERE hh.horseLeg.bookingHorse.id = :horseId
                AND hh.microchipMatched = true AND hh.handover.handedAt >= :since
                AND hh.handover.status = com.example.racehorse_transport.entity.enums.HandoverStatus.DONE
                AND hh.handover.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.PICKUP
            """)
    boolean receivedAfterPickupMiss(Long horseId, Instant since);

    List<BookingHorse> findByBooking_Id(Long bookingId);

    @Query("select h.booking.id from BookingHorse h where h.id = :id")
    Optional<Long> findBookingId(Long id);

    @Query("""
            SELECT h FROM BookingHorse h JOIN FETCH h.horse horse LEFT JOIN FETCH horse.horseType JOIN FETCH h.stallType
            LEFT JOIN FETCH h.deliveryAddress a LEFT JOIN FETCH a.country
            WHERE h.booking.id = :bookingId ORDER BY h.id
            """)
    List<BookingHorse> findForDetail(Long bookingId);

    @EntityGraph(attributePaths = {"booking", "horse", "stallType", "deliveryAddress"})
    @Query("""
            SELECT h FROM BookingHorse h WHERE h.booking.departure.id = :departureId
            AND h.status = :status AND h.booking.status IN :bookingStatuses ORDER BY h.booking.id, h.id
            """)
    List<BookingHorse> findForLinehaul(Long departureId, BookingHorseStatus status, Collection<BookingStatus> bookingStatuses);

    @Query("""
            SELECT DISTINCT b FROM BookingHorse h JOIN h.booking b JOIN FETCH b.specialist
            WHERE h.horse.id = :horseId AND h.status = ACTIVE AND b.status IN :statuses
            """)
    List<Booking> findAssignedBookingsForHorse(
            Long horseId, Collection<BookingStatus> statuses);

    // Bảng phụ trách
    @EntityGraph(attributePaths = {"horse", "booking"})
    List<BookingHorse> findByStatusAndBooking_StatusIn(BookingHorseStatus status, Collection<BookingStatus> bookingStatuses);

    @Query("""
            SELECT DISTINCT b.code FROM BookingHorse h JOIN h.booking b
            WHERE h.horse.id IN :horseIds AND h.status = :horseStatus AND b.status NOT IN :endedStatuses
            ORDER BY b.code
            """)
    List<String> findBusyBookingCodes(Collection<Long> horseIds, BookingHorseStatus horseStatus,
            Collection<BookingStatus> endedStatuses);

    // Cùng điều kiện với findBusyBookingCodes, kèm con ngựa
    @Query("""
            SELECT h.horse.id AS horseId, b.code AS bookingCode FROM BookingHorse h JOIN h.booking b
            WHERE h.horse.id IN :horseIds AND h.status = :horseStatus AND b.status NOT IN :endedStatuses
            ORDER BY b.code
            """)
    List<HorseBooking> findBusyHorses(Collection<Long> horseIds, BookingHorseStatus horseStatus,
            Collection<BookingStatus> endedStatuses);

    @Query("""
            SELECT h.booking.id AS bookingId, h.horse.name AS horseName, insurance.coverageOption AS coverageOption,
                   insurance.status AS insuranceStatus
            FROM BookingHorse h LEFT JOIN HorseInsurance insurance ON insurance.bookingHorse = h
            WHERE h.booking.id IN :bookingIds AND h.status = ACTIVE
            ORDER BY h.id
            """)
    List<SpecialistHorse> findSpecialistHorses(Collection<Long> bookingIds);

    interface SpecialistHorse {
        Long getBookingId();
        String getHorseName();
        InsuranceCoverageOption getCoverageOption();
        HorseInsuranceStatus getInsuranceStatus();
    }

    interface HorseBooking {
        Long getHorseId();

        String getBookingCode();
    }

    interface DepartureStallCount {
        Long getDepartureId();
        String getStallTypeCode();
        Long getHorseCount();
    }

    @Query("""
            SELECT b.departure.id AS departureId, h.stallType.code AS stallTypeCode, COUNT(h) AS horseCount
            FROM BookingHorse h JOIN h.booking b
            WHERE b.departure.id IN :departureIds AND b.status IN :statuses AND h.status = :horseStatus
            GROUP BY b.departure.id, h.stallType.code
            """)
    List<DepartureStallCount> countByDepartureAndStall(Collection<Long> departureIds, Collection<BookingStatus> statuses,
            BookingHorseStatus horseStatus);
}
