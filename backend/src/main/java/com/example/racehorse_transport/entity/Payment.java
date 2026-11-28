package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.PaymentMethod;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Khoản phải thu hoặc phải hoàn. Số tiền luôn dương, chiều tiền xác định bằng paymentType.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    // Bắt buộc với cọc và số dư (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 30, nullable = false)
    private PaymentType paymentType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30, nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private PaymentStatus status;

    @Column(name = "due_at")
    private Instant dueAt;

    // Lúc lập khoản thu thêm, mốc tính công nợ
    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "extended_at")
    private Instant extendedAt;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    @Column(name = "dispute_reason", length = 500)
    private String disputeReason;

    @Column(name = "dispute_resolved_at")
    private Instant disputeResolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_decision", length = 20)
    private com.example.racehorse_transport.entity.enums.DisputeDecision disputeDecision;

    @Column(name = "dispute_response", length = 500)
    private String disputeResponse;

    @Column(name = "written_off_at")
    private Instant writtenOffAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "written_off_by")
    private AppUser writtenOffBy;

    @Column(name = "write_off_reason", length = 500)
    private String writeOffReason;

    @Column(name = "block_released_at")
    private Instant blockReleasedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    // Khoản được hoàn, chỉ có khi REFUND (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "refunded_payment_id")
    private Payment refundedPayment;

    @OneToMany(mappedBy = "extraChargePayment")
    private List<CostItem> extraChargeCosts = new ArrayList<>();
}
