package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.HealthStatus;

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

import java.math.BigDecimal;
import java.time.Instant;

// Một lần đo sức khỏe ngựa: tại điểm dừng, trong lúc lưu trú, hoặc trên đường.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "horse_health_log")
public class HorseHealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_horse_id", nullable = false)
    private BookingHorse bookingHorse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private Staff recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    // Đo ở đâu: trên đường, tại điểm dừng, hoặc trong lưu trú; nhiều nhất một (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vehicle_trip_id")
    private VehicleTrip vehicleTrip;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "trip_stop_id")
    private TripStop tripStop;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "horse_stay_id")
    private HorseStay horseStay;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", length = 30, nullable = false)
    private HealthStatus healthStatus;

    @Column(name = "temperature_celsius", precision = 4, scale = 1)
    private BigDecimal temperatureCelsius;

    @Column(name = "heart_rate_bpm")
    private Short heartRateBpm;

    @Column(name = "respiration_rate_per_minute")
    private Short respirationRatePerMinute;

    @Column(name = "weight_kg", precision = 6, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "symptoms", columnDefinition = "text")
    private String symptoms;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}
