package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;

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
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

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

    @Column(name = "waybill_no", length = 30)
    private String waybillNo;

    @Column(name = "emergency_spend_limit", precision = 15, scale = 2)
    private BigDecimal emergencySpendLimit;

    // Đơn này là chuyến hồi hương của đơn nào
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "return_of_booking_id")
    private Booking returnOf;

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
