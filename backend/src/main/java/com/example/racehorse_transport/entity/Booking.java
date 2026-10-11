package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "BOOKING")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BookingID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerID")
    private Customer customerID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RouteID")
    private Route routeID;

    @Column(name = "BookingDate")
    private Instant bookingDate;

    @Column(name = "DepartureDate")
    private Instant departureDate;

    @Size(max = 50)
    @Column(name = "Status", length = 50)
    private String status;

    @Size(max = 50)
    @Column(name = "BookingType", length = 50)
    private String bookingType; // domestic, international

    @Size(max = 255)
    @Nationalized
    @Column(name = "ConsignorName")
    private String consignorName;

    @Size(max = 50)
    @Column(name = "ConsignorPhone", length = 50)
    private String consignorPhone;

    @Size(max = 255)
    @Nationalized
    @Column(name = "ConsigneeName")
    private String consigneeName;

    @Size(max = 50)
    @Column(name = "ConsigneePhone", length = 50)
    private String consigneePhone;

    @Size(max = 255)
    @Nationalized
    @Column(name = "PickupAddress")
    private String pickupAddress;

    @Size(max = 255)
    @Nationalized
    @Column(name = "DropoffAddress")
    private String dropoffAddress;

    @Column(name = "TotalHorses")
    private Integer totalHorses;

    @Size(max = 50)
    @Column(name = "WaybillNo", length = 50)
    private String waybillNo;

    @Nationalized
    @Lob
    @Column(name = "DetailsJson")
    private String detailsJson;
}