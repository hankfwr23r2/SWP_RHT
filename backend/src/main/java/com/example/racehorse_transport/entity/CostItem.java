package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.CostPayerType;

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
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

// Chi phí phát sinh. Hóa đơn hoặc biên lai là document trỏ về dòng này.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cost_item")
public class CostItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private CostCategory category;

    @Column(name = "description", length = 500, nullable = false)
    private String description;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    // snapshot: số ngày tính trong dòng phí giữ ngựa này
    @Column(name = "stabling_days")
    private Long stablingDays;

    // Ai chịu khoản này
    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", length = 30, nullable = false)
    private CostPayerType payerType;

    // Gắn với đơn, lượt xe hoặc sự cố; ít nhất một (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vehicle_trip_id")
    private VehicleTrip vehicleTrip;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    // Ví dụ phí chờ khi người nhận vắng mặt
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "handover_id")
    private Handover handover;

    // Ví dụ phí gửi chuồng
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "horse_stay_id")
    private HorseStay horseStay;

    // Đã hỏi ý kiến khách trước khi chi
    @Column(name = "is_customer_consulted", nullable = false)
    private Boolean customerConsulted = false;

    @Column(name = "customer_agreed")
    private Boolean customerAgreed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_response_by")
    private AppUser customerResponseBy;

    @Column(name = "customer_response_at")
    private Instant customerResponseAt;

    @Column(name = "customer_response_due_at")
    private Instant customerResponseDueAt;

    @Column(name = "phone_consent_note", length = 500)
    private String phoneConsentNote;

    @Column(name = "emergency_override_reason", length = 500)
    private String emergencyOverrideReason;

    @Column(name = "agent_recovery_required", nullable = false)
    private boolean agentRecoveryRequired;

    @Column(name = "agent_recovered_at")
    private Instant agentRecoveredAt;

    // snapshot: phần phí giữ đã cấn vào tiền cọc của đơn hủy
    @Column(name = "stabling_deposit_credit", nullable = false, precision = 15, scale = 2)
    private BigDecimal stablingDepositCredit = BigDecimal.ZERO;

    // snapshot: phần phí giữ được cấn bằng cước giao chưa chạy khi hủy vì không có người nhận
    @Column(name = "stabling_delivery_credit", nullable = false, precision = 15, scale = 2)
    private BigDecimal stablingDeliveryCredit = BigDecimal.ZERO;

    @Column(name = "incurred_at", nullable = false)
    private Instant incurredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private Staff recordedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "approved_by")
    private Staff approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    // Khoản thu thêm từ khách cho chi phí này
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "extra_charge_payment_id")
    private Payment extraChargePayment;
}
