package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingHorseRepository extends JpaRepository<BookingHorse, Long> {

    List<BookingHorse> findByBooking_Id(Long bookingId);

    @Query("select h.booking.id from BookingHorse h where h.id = :id")
    Optional<Long> findBookingId(Long id);

    @Query("""
            SELECT h FROM BookingHorse h JOIN FETCH h.horse JOIN FETCH h.stallType LEFT JOIN FETCH h.deliveryAddress a LEFT JOIN FETCH a.country
            WHERE h.booking.id = :bookingId ORDER BY h.id
            """)
    List<BookingHorse> findForDetail(Long bookingId);

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
