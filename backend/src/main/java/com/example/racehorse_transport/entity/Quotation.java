package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.QuotationStatus;

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
import java.time.LocalDate;

// Một phiên bản báo giá của đơn. Bất biến sau khi SENT.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "quotation")
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "version", nullable = false)
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceList priceList;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    // snapshot: tỷ lệ cọc chép từ price_list lúc lập báo giá
    @Column(name = "deposit_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal depositRate;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_pickup_date", nullable = false)
    private LocalDate confirmedPickupDate;

    @Column(name = "confirmed_departure_date", nullable = false)
    private LocalDate confirmedDepartureDate;

    @Column(name = "estimated_delivery_date", nullable = false)
    private LocalDate estimatedDeliveryDate;

    // derived: QuotationService (Σ dòng báo giá)
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    // derived: QuotationService (phần thu hộ)
    @Column(name = "pass_through_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal passThroughAmount;

    // derived: QuotationService (cọc, gồm toàn bộ phần thu hộ)
    @Column(name = "deposit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private QuotationStatus status;

    // NULL khi còn DRAFT
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "sent_by")
    private Staff sentBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
