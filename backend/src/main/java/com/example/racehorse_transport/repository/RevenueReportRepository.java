package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

// Báo cáo doanh thu của quản lý (B6): chỉ đọc thanh toán, báo giá và chi phí
public interface RevenueReportRepository extends Repository<Payment, Long> {

    // Mọi khoản thanh toán của các đơn có khoản cọc trả trong [from, to)
    @Query("""
            SELECT p FROM Payment p JOIN FETCH p.booking b JOIN FETCH b.customer
            WHERE b.id IN (SELECT d.booking.id FROM Payment d WHERE d.paymentType = :deposit AND d.status = :paid
                           AND d.paidAt >= :from AND d.paidAt < :to)
            ORDER BY b.id, p.id
            """)
    List<Payment> paymentsOfBookingsDepositedIn(PaymentType deposit, PaymentStatus paid, Instant from, Instant to);

    @Query("SELECT q FROM Quotation q WHERE q.booking.id IN :bookingIds AND q.status = :status")
    List<Quotation> quotationsOf(Collection<Long> bookingIds, QuotationStatus status);

    @Query("""
            SELECT c FROM CostItem c WHERE c.payerType = :payer AND c.approvedAt IS NOT NULL
            AND c.incurredAt >= :from AND c.incurredAt < :to
            """)
    List<CostItem> approvedCosts(CostPayerType payer, Instant from, Instant to);
}
