package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "HORSE_HEALTH_LOG")
public class HorseHealthLog {
    @Id
    @Column(name = "HealthLogID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HorseID")
    private Horse horseID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingID")
    private Booking bookingID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReportBy")
    private Staff reportBy;

    @Size(max = 255)
    @Nationalized
    @Column(name = "healthStatus")
    private String healthStatus;

    @Column(name = "tempature", precision = 5, scale = 2)
    private BigDecimal tempature;

    @Column(name = "weight", precision = 10, scale = 2)
    private BigDecimal weight;

    @Nationalized
    @Lob
    @Column(name = "symptoms")
    private String symptoms;


}