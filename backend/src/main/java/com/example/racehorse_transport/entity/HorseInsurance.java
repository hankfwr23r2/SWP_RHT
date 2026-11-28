package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Bảo hiểm cho từng con trong đơn (1–1 với booking_horse).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "horse_insurance")
public class HorseInsurance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_horse_id", nullable = false, unique = true)
    private BookingHorse bookingHorse;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_option", length = 30, nullable = false)
    private InsuranceCoverageOption coverageOption;

    @Column(name = "insurer_name", length = 200)
    private String insurerName;

    @Column(name = "premium_amount", precision = 15, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "refunded_premium_amount", precision = 15, scale = 2)
    private BigDecimal refundedPremiumAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "quote_expires_at")
    private Instant quoteExpiresAt;

    @Column(name = "policy_number", length = 50)
    private String policyNumber;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    // Bản cam kết miễn trách, bắt buộc khi NONE (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "waiver_terms_acceptance_id")
    private TermsAcceptance waiverTermsAcceptance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private HorseInsuranceStatus status;

    @Column(name = "revision_reason", columnDefinition = "text")
    private String revisionReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "revision_requested_by")
    private Staff revisionRequestedBy;

    @Column(name = "revision_requested_at")
    private Instant revisionRequestedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "confirmed_by")
    private Staff confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
