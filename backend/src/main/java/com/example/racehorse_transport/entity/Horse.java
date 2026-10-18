package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "HORSE")
public class Horse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HorseID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HorseTypeID")
    private HorseType horseTypeID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerID")
    private Customer customerID;

    @Size(max = 255)
    @Nationalized
    @Column(name = "HorseName")
    private String horseName;

    @Size(max = 100)
    @Column(name = "MicrochipID", length = 100)
    private String microchipID;

    @Size(max = 20)
    @Column(name = "Gender", length = 20)
    private String gender;

    @Size(max = 100)
    @Nationalized
    @Column(name = "breed", length = 100)
    private String breed;

    @Size(max = 50)
    @Nationalized
    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "Age")
    private Integer age;

    @Column(name = "Weight", precision = 10, scale = 2)
    private BigDecimal weight;

    @Column(name = "identification_marks", columnDefinition = "TEXT")
    private String identificationMarks;

    @Column(name = "completed_trips", nullable = false)
    private Integer completedTrips;

    // Existing PostgreSQL installations store this value as epoch milliseconds (BIGINT).
    @Column(name = "created_at")
    private Long createdAt;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;

    @Size(max = 255)
    @Nationalized
    @Column(name = "current_location_name")
    private String currentLocationName;

    @Size(max = 10)
    @Column(name = "current_country", length = 10)
    private String currentCountry;

    @Column(name = "location_updated_at")
    private Long locationUpdatedAt;

    @Size(max = 50)
    @Column(name = "location_source", length = 50)
    private String locationSource;

    @Size(max = 50)
    @Column(name = "transit_status", length = 50)
    private String transitStatus;

}

