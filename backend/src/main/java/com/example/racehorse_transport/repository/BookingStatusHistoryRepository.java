package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingStatusHistory;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {

    @EntityGraph(attributePaths = {"previousDeparture", "previousDeparture.mergedIntoDeparture"})
    Optional<BookingStatusHistory> findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(Long bookingId, BookingStatus status);

    @EntityGraph(attributePaths = "changedBy")
    Optional<BookingStatusHistory> findFirstByBooking_IdAndFromStatusAndToStatusOrderByChangedAtDescIdDesc(
            Long bookingId, BookingStatus fromStatus, BookingStatus toStatus);

    long countByBooking_IdAndToStatusAndCauseType(Long bookingId, BookingStatus status, ScheduleChangeCause cause);

    @Query("""
            SELECT DISTINCT h.booking.id FROM BookingStatusHistory h
            WHERE h.booking.status = :status AND h.toStatus = :status AND h.changedAt <= :cutoff
            ORDER BY h.booking.id
            """)
    List<Long> findBookingIdsWithStatusBefore(BookingStatus status, Instant cutoff);

    @Query("""
            SELECT h.booking.id FROM BookingStatusHistory h
            WHERE h.booking.status = :status AND h.toStatus = :status
            GROUP BY h.booking.id
            HAVING MAX(h.changedAt) <= :cutoff
            """)
    List<Long> findBookingIdsWithLatestStatusEntryThrough(BookingStatus status, Instant cutoff);

    Optional<BookingStatusHistory> findFirstByBooking_IdOrderByChangedAtDescIdDesc(Long bookingId);

    List<BookingStatusHistory> findByBooking_IdOrderByChangedAtAscIdAsc(Long bookingId);
}
