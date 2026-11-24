package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Document;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    @Query("""
            SELECT d FROM Document d JOIN FETCH d.documentRequirement JOIN FETCH d.documentType
            WHERE d.booking.id = :bookingId
                OR d.bookingHorse.id IN (SELECT h.id FROM BookingHorse h WHERE h.booking.id = :bookingId AND h.status = ACTIVE)
                OR d.horse.id IN (SELECT h.horse.id FROM BookingHorse h WHERE h.booking.id = :bookingId AND h.status = ACTIVE)
            ORDER BY d.id
            """)
    List<Document> findForBookingReview(Long bookingId);

    // Biên bản giao nhận (HANDOVER_RECORD) đã tải cho lần giao nhận chưa
    boolean existsByHandover_IdAndDocumentType_Code(Long handoverId, String documentTypeCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :id")
    Optional<Document> findForReviewUpdate(Long id);
}
