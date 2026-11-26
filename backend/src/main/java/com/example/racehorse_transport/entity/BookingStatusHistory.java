package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;

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

import java.time.Instant;

// Nhật ký đổi trạng thái đơn. Ghi trong cùng transaction với lần đổi trạng thái.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_status_history")
public class BookingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    // NULL ở dòng đầu tiên
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private BookingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30, nullable = false)
    private BookingStatus toStatus;

    // NULL = hệ thống tự đổi (scheduler)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private AppUser changedBy;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "changed_at", nullable = false, insertable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cause_type", length = 30)
    private ScheduleChangeCause causeType;

    // snapshot: chuyến vừa lỡ, kể cả khi đơn đã nhả chỗ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_departure_id")
    private Departure previousDeparture;
}
