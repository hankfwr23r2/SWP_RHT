package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.TransferMethod;

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

import org.hibernate.annotations.Generated;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Phần đầu đơn. booking.status chỉ có mốc thương mại.
@Getter
@Setter
@NoArgsConstructor
@org.hibernate.annotations.DynamicUpdate
@Entity
@Table(name = "booking")
public class Booking {

    @Column(name = "collection_code", length = 6)
    private String collectionCode;

    @Column(name = "collection_failed_attempts", nullable = false)
    private int collectionFailedAttempts;

    @Column(name = "collection_confirmed", nullable = false)
    private boolean collectionConfirmed;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialist_id")
    private Staff specialist;

    @Column(name = "code", length = 30, nullable = false)
    private String code;

    // Người đặt đơn
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", length = 30, nullable = false)
    private BookingType bookingType;

    // Chuyến trục chính. NULL khi chưa gán chuyến hoặc khi đơn chạy thẳng
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "departure_id")
    private Departure departure;

    @Column(name = "requested_pickup_date", nullable = false)
    private LocalDate requestedPickupDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_method", length = 30, nullable = false)
    private TransferMethod pickupMethod = TransferMethod.COMPANY;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", length = 30, nullable = false)
    private TransferMethod deliveryMethod = TransferMethod.COMPANY;

    @Column(name = "waybill_number", length = 30)
    private String waybillNumber;

    @Column(name = "delivery_confirmed_at")
    private Instant deliveryConfirmedAt;

    @Column(name = "emergency_spending_limit", precision = 15, scale = 2)
    private BigDecimal emergencySpendingLimit;

    // Đơn này là chuyến hồi hương của đơn nào
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "original_booking_id")
    private Booking originalBooking;

    // Chỉ đổi qua BookingStatusService, kèm một dòng booking_status_history
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private BookingStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
