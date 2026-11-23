package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface BookingHorseRepository extends JpaRepository<BookingHorse, Long> {

    List<BookingHorse> findByBooking_Id(Long bookingId);

    @Query("""
            SELECT h FROM BookingHorse h JOIN FETCH h.horse JOIN FETCH h.stallType
            WHERE h.booking.id = :bookingId ORDER BY h.id
            """)
    List<BookingHorse> findForDetail(Long bookingId);

    @Query("""
            SELECT DISTINCT b.code FROM BookingHorse h JOIN h.booking b
            WHERE h.horse.id IN :horseIds AND h.status = :horseStatus AND b.status NOT IN :endedStatuses
            ORDER BY b.code
            """)
    List<String> findBusyBookingCodes(Collection<Long> horseIds, BookingHorseStatus horseStatus,
            Collection<BookingStatus> endedStatuses);
}
