package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    List<Quotation> findByBooking_IdOrderByVersion(Long bookingId);

    Optional<Quotation> findByBooking_IdAndStatus(Long bookingId, QuotationStatus status);

    @Query("select q.booking.id from Quotation q where q.id = :id")
    Optional<Long> findBookingId(Long id);

    @Query("""
            select q.id from Quotation q where q.booking.status = :bookingStatus
            and q.status in :statuses and q.expiresAt <= :now order by q.booking.id, q.id
            """)
    List<Long> findExpirableIds(BookingStatus bookingStatus, Collection<QuotationStatus> statuses, Instant now);
}
