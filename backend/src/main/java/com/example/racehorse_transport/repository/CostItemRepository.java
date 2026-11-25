package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.enums.CostCategory;

import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CostItemRepository extends JpaRepository<CostItem, Long> {
    @Query("select c.vehicleTrip.id from CostItem c where c.id = :id")
    Optional<Long> findVehicleTripId(Long id);
    @Query("""
            SELECT c FROM CostItem c LEFT JOIN FETCH c.extraChargePayment
            WHERE c.horseStay.id = :stayId OR (
                c.category = com.example.racehorse_transport.entity.enums.CostCategory.REDELIVERY
                AND EXISTS (SELECT l.id FROM HorseLeg l, HorseStay s
                            WHERE s.id = :stayId AND l.bookingHorse = s.bookingHorse
                              AND l.vehicleTrip = c.vehicleTrip AND l.bookingHorse.booking = c.booking
                              AND l.replacedHorseLeg IS NOT NULL AND (s.failedDeliveryLeg IS NULL OR l.replacedHorseLeg = s.failedDeliveryLeg)
                              AND (s.failedDeliveryLeg IS NOT NULL OR l.plannedStartAt >= s.actualStartAt)
                              AND l.status <> com.example.racehorse_transport.entity.enums.HorseLegStatus.CANCELLED
                              AND (s.actualEndAt IS NULL OR c.incurredAt <= s.actualEndAt)))
            """)
    List<CostItem> findStablingClockCosts(Long stayId);

    boolean existsByBooking_IdAndVehicleTrip_IdAndCategory(Long bookingId, Long tripId,
            com.example.racehorse_transport.entity.enums.CostCategory category);
    @Query("select c.incident.id from CostItem c where c.id = :id")
    Optional<Long> findIncidentId(Long id);

    @Query("select c.booking.id from CostItem c where c.id = :id")
    Optional<Long> findBookingId(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CostItem c where c.id = :id")
    Optional<CostItem> findForUpdate(Long id);


    List<CostItem> findByHorseStay_Id(Long horseStayId);

    // Phí giữ cộng dồn qua mọi lần giữ của một con trong đơn
    List<CostItem> findByHorseStay_BookingHorse_Id(Long bookingHorseId);

    @Query("SELECT COALESCE(SUM(c.stablingDepositCredit), 0) FROM CostItem c WHERE c.booking.id = :bookingId")
    BigDecimal sumStablingDepositCredit(Long bookingId);

    boolean existsByHorseStay_Id(Long horseStayId);

    boolean existsByHorseStay_IdAndCategory(Long horseStayId, CostCategory category);

    @EntityGraph(attributePaths = {"booking", "incident", "recordedBy", "approvedBy"})
    List<CostItem> findByIncident_IdOrderByIncurredAt(Long incidentId);

    @EntityGraph(attributePaths = {"booking", "incident", "recordedBy", "approvedBy"})
    List<CostItem> findByBooking_IdOrderByIncurredAt(Long bookingId);

    // Tổng các khoản khách chịu đã duyệt mà chưa hỏi khách (dùng hạn mức chi khẩn cấp)
    @Query("""
            SELECT coalesce(sum(c.amount), 0) FROM CostItem c LEFT JOIN c.horseStay s
            WHERE c.booking.id = :bookingId AND c.payerType IN :payers AND c.customerConsulted = false AND c.approvedAt IS NOT NULL
              AND c.category NOT IN (com.example.racehorse_transport.entity.enums.CostCategory.REDELIVERY,
                                     com.example.racehorse_transport.entity.enums.CostCategory.PLANNED_STABLING)
              AND (s.id IS NULL OR s.stayType <> com.example.racehorse_transport.entity.enums.HorseStayType.STABLING
                   OR c.category <> com.example.racehorse_transport.entity.enums.CostCategory.STABLING)
            """)
    BigDecimal sumApprovedWithoutConsulting(@Param("bookingId") Long bookingId, @Param("payers") Collection<CostPayerType> payers);

    @Query("""
            SELECT coalesce(sum(c.amount), 0) FROM CostItem c
            WHERE c.booking.id = :bookingId AND c.payerType IN :payers AND c.approvedAt IS NOT NULL
            """)
    BigDecimal sumApprovedForBooking(@Param("bookingId") Long bookingId, @Param("payers") Collection<CostPayerType> payers);

    // Báo cáo chuyến: chi phí gắn thẳng với lượt xe hoặc với sự cố của lượt xe
    @Query("""
            SELECT c FROM CostItem c LEFT JOIN c.incident i
            WHERE c.vehicleTrip.id IN :tripIds OR i.vehicleTrip.id IN :tripIds
            ORDER BY c.incurredAt, c.id
            """)
    List<CostItem> findOfTrips(@Param("tripIds") Collection<Long> tripIds);
}
