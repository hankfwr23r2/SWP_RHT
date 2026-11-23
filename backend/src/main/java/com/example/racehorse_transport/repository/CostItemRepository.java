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

    @EntityGraph(attributePaths = {"booking", "incident", "recordedBy", "approvedBy"})
    List<CostItem> findByIncident_IdOrderByIncurredAt(Long incidentId);

    @EntityGraph(attributePaths = {"booking", "incident", "recordedBy", "approvedBy"})
    List<CostItem> findByBooking_IdOrderByIncurredAt(Long bookingId);

    // Tổng các khoản khách chịu đã duyệt mà chưa hỏi khách (dùng hạn mức chi khẩn cấp)
    @Query("""
            SELECT coalesce(sum(c.amount), 0) FROM CostItem c
            WHERE c.booking.id = :bookingId AND c.payerType IN :payers AND c.customerConsulted = false AND c.approvedAt IS NOT NULL
            """)
    BigDecimal sumApprovedWithoutConsulting(@Param("bookingId") Long bookingId, @Param("payers") Collection<CostPayerType> payers);
}
