package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Document;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("SELECT d.evidenceHorseLeg.id FROM Document d WHERE d.handover.id = :handoverId AND d.documentType.code = :code AND d.evidenceHorseLeg IS NOT NULL")
    java.util.Set<Long> findEvidenceHorseLegIds(Long handoverId, String code);
    @Query("""
            SELECT d FROM Document d JOIN FETCH d.documentRequirement JOIN FETCH d.documentType
            WHERE d.booking.id = :bookingId
                OR d.bookingHorse.id IN (SELECT h.id FROM BookingHorse h WHERE h.booking.id = :bookingId AND h.status = ACTIVE)
                OR d.horse.id IN (SELECT h.horse.id FROM BookingHorse h WHERE h.booking.id = :bookingId AND h.status = ACTIVE)
            ORDER BY d.id
            """)
    List<Document> findForBookingReview(Long bookingId);

    @Query("""
            SELECT d FROM Document d JOIN FETCH d.documentType
                JOIN FETCH d.horseInsurance i JOIN FETCH i.bookingHorse h
            WHERE h.booking.id = :bookingId AND d.documentType.code = :typeCode
                AND d.id = (SELECT MAX(previous.id) FROM Document previous
                            WHERE previous.horseInsurance = i AND previous.documentType = d.documentType)
            ORDER BY d.id
            """)
    List<Document> findLatestInsuranceDocuments(Long bookingId, String typeCode);

    @Query("""
            SELECT d FROM Document d JOIN FETCH d.documentType
            WHERE d.horse.id IN (SELECT h.horse.id FROM BookingHorse h WHERE h.booking.id = :bookingId AND h.status = ACTIVE)
                AND d.documentRequirement IS NULL AND d.status = APPROVED
            ORDER BY d.id
            """)
    List<Document> findApprovedHorseDocumentsWithoutRequirement(Long bookingId);

    @Query("""
            SELECT d FROM Document d JOIN FETCH d.documentType
            WHERE EXISTS (
                SELECT 1 FROM HandoverDocument paper
                WHERE paper.document = d AND paper.direction = RECEIVED AND paper.handover.status = DONE
                  AND EXISTS (
                      SELECT 1 FROM HandoverHorse hh WHERE hh.handover = paper.handover
                          AND hh.microchipMatched = true AND hh.horseLeg.bookingHorse.booking.id = :bookingId
                  )
            )
              AND (d.booking.id = :bookingId
                OR d.bookingHorse.id IN (SELECT line.id FROM BookingHorse line WHERE line.booking.id = :bookingId)
                OR d.horse.id IN (SELECT h.horse.id FROM BookingHorse h WHERE h.booking.id = :bookingId))
            ORDER BY d.id
            """)
    List<Document> findOriginalsEverReceived(Long bookingId);

    // Biên bản giao nhận (HANDOVER_RECORD) đã tải cho lần giao nhận chưa
    boolean existsByHandover_IdAndEvidenceHorseLeg_IdAndDocumentType_Code(Long handoverId, Long horseLegId, String code);

    boolean existsByHandover_IdAndDocumentType_Code(Long handoverId, String documentTypeCode);

    @Query("SELECT DISTINCT d.vehicleTrip.id FROM Document d WHERE d.vehicleTrip.id IN :tripIds AND d.documentType.code = :code")
    Set<Long> findTripIdsWithPhoto(Collection<Long> tripIds, String code);

    @Query("""
            SELECT d FROM Document d JOIN FETCH d.documentType JOIN FETCH d.handover h
            WHERE h.id IN :handoverIds AND h.status = DONE
              AND d.documentType.code IN ('HANDOVER_RECORD', 'HANDOVER_PHOTO')
              AND EXISTS (SELECT 1 FROM HandoverHorse hh WHERE hh.handover = h
                          AND hh.horseLeg.bookingHorse.booking.id = :bookingId AND hh.microchipMatched = true
                          AND (d.evidenceHorseLeg IS NULL OR hh.horseLeg = d.evidenceHorseLeg))
              AND (d.evidenceHorseLeg IS NOT NULL OR (
              NOT EXISTS (SELECT 1 FROM HandoverHorse hh WHERE hh.handover = h
                              AND hh.horseLeg.bookingHorse.booking.id <> :bookingId)
              AND NOT EXISTS (SELECT 1 FROM HorseLeg leg
                              WHERE (leg.boardTripStop = h.tripStop OR leg.alightTripStop = h.tripStop)
                                AND leg.bookingHorse.booking.id <> :bookingId)))
            ORDER BY d.id
            """)
    List<Document> findHandoverEvidenceForBooking(Long bookingId, List<Long> handoverIds);

    // Biên lai, hóa đơn của một chi phí và ảnh hiện trường của một sự cố (B4)
    @EntityGraph(attributePaths = {"documentType", "uploadedBy"})
    List<Document> findByCostItem_IdOrderById(Long costItemId);

    @EntityGraph(attributePaths = {"documentType", "uploadedBy"})
    List<Document> findByIncident_IdOrderById(Long incidentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :id")
    Optional<Document> findForReviewUpdate(Long id);
}
