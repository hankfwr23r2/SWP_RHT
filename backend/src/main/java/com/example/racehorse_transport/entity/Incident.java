package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;

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

// Sự cố. Gắn với đơn, lượt xe hoặc chặng của một con ngựa; ít nhất một (CHECK ở DB).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    // Một sự cố xe có thể ảnh hưởng nhiều đơn
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vehicle_trip_id")
    private VehicleTrip vehicleTrip;

    // Sự cố của riêng một con ngựa
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "horse_leg_id")
    private HorseLeg horseLeg;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", length = 30, nullable = false)
    private IncidentType incidentType;

    // Nguyên nhân, quyết định ai chịu chi phí
    @Enumerated(EnumType.STRING)
    @Column(name = "cause_type", length = 30, nullable = false)
    private IncidentCauseType causeType;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text", nullable = false)
    private String description;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by", nullable = false)
    private Staff reportedBy;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "reported_at", nullable = false, insertable = false, updatable = false)
    private Instant reportedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private IncidentStatus status;

    // Người xác nhận ngựa đủ sức khỏe đi tiếp
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "fit_confirmed_by")
    private Staff fitConfirmedBy;

    @Column(name = "fit_confirmed_at")
    private Instant fitConfirmedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
