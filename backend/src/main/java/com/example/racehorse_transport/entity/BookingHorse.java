package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
@Entity
@Table(name = "BOOKING_HORSE")
public class BookingHorse {
    @EmbeddedId
    private BookingHorseId id;

    @MapsId("bookingID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BookingID", nullable = false)
    private Booking bookingID;

    @MapsId("horseID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "HorseID", nullable = false)
    private Horse horseID;

    @Column(name = "AddedDate")
    private Instant addedDate;

    @Column(name = "TotalHorseServicePrice", precision = 18, scale = 2)
    private BigDecimal totalHorseServicePrice;

    @Column(name = "snapshot_order")
    private Integer snapshotOrder;
    @Column(name = "horse_reference", length = 100)
    private String horseReference;
    @Column(name = "horse_name", length = 255)
    private String horseName;
    @Column(name = "microchip", length = 255)
    private String microchip;
    @Column(name = "breed", length = 255)
    private String breed;
    @Column(name = "sex", length = 50)
    private String sex;
    @Column(name = "stall", length = 100)
    private String stall;
    @Column(name = "feed_package", length = 100)
    private String feedPackage;
    @Column(name = "water_plan", length = 100)
    private String waterPlan;
    @Column(name = "insurance_opted")
    private Boolean insuranceOpted;
    @Column(name = "eligibility_status", length = 50)
    private String eligibilityStatus;
    @Column(name = "unfit_info_json", columnDefinition = "TEXT")
    private String unfitInfoJson;
    @Column(name = "selected_services_present", nullable = false)
    private boolean selectedServicesPresent;
    @Column(name = "selected_service_codes_present", nullable = false)
    private boolean selectedServiceCodesPresent;
    @Column(name = "stall_service_code", length = 100)
    private String stallServiceCode;
    @Column(name = "feed_service_code", length = 100)
    private String feedServiceCode;
    @Column(name = "water_service_code", length = 100)
    private String waterServiceCode;
    @Column(name = "insurance_service_code", length = 100)
    private String insuranceServiceCode;

    @JsonIgnore
    @OneToOne(mappedBy = "bookingHorse", cascade = CascadeType.ALL, orphanRemoval = true)
    private BookingHorseInsurance insurancePolicy;
}
