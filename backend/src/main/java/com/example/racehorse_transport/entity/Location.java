package com.example.racehorse_transport.entity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "LOCATION")
public class Location {
    @Id
    @Column(name = "LocationID", nullable = false)
    private Integer id;

    @Column(name = "locationCode", unique = true, length = 50)
    private String locationCode;

    @NotNull
    @Size(max = 20)
    @Column(name = "LocationType", nullable = false, length = 20)
    @Pattern(regexp = "Warehouse|RidingClub|RaceTrack|Stable|RestStop|BorderGate|RescuePoint|City")
    private String locationType;

    @NotNull
    @Size(max = 255)
    @Nationalized
    @Column(name = "LocationName")
    private String locationName;

    @NotNull
    @Size(max = 255)
    @Nationalized
    @Column(name = "address")
    private String address;

    @NotNull
    @Size(max = 100)
    @Nationalized
    @Column(name = "city", length = 100)
    private String city;

    @NotNull
    @Size(max = 100)
    @Nationalized
    @Column(name = "province", length = 100)
    private String province;
    
    @NotNull
    @Size(max = 100)
    @Nationalized
    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "openingMinutes")
    private Integer openingMinutes;

    @Column(name = "closingMinutes")
    private Integer closingMinutes;

    @Column(name = "contactPhone", length = 100)
    private String contactPhone;

    @Column(name = "destinationCountry", length = 2)
    private String destinationCountry;
}