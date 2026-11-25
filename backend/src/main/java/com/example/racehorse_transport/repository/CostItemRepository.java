package com.example.racehorse_transport.repository;

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

    List<CostItem> findByHorseStay_Id(Long horseStayId);

    @Query("SELECT COALESCE(SUM(c.stablingDepositCredit), 0) FROM CostItem c WHERE c.booking.id = :bookingId")
    BigDecimal sumStablingDepositCredit(Long bookingId);

    boolean existsByHorseStay_Id(Long horseStayId);

    @EntityGraph(attributePaths = {"booking", "incident", "recordedBy", "approvedBy"})
    List<CostItem> findByIncident_IdOrderByIncurredAt(Long incidentId);

    @EntityGraph(attributePaths = {"booking", "incident", "recordedBy", "approvedBy"})
    List<CostItem> findByBooking_IdOrderByIncurredAt(Long bookingId);

    // Tổng các khoản khách chịu đã duyệt mà chưa hỏi khách (dùng hạn mức chi khẩn cấp)
    @Query("""
            SELECT coalesce(sum(c.amount), 0) FROM CostItem c LEFT JOIN c.horseStay s
            WHERE c.booking.id = :bookingId AND c.payerType IN :payers AND c.customerConsulted = false AND c.approvedAt IS NOT NULL
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
