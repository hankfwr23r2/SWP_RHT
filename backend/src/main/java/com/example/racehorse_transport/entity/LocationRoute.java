package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "LOCATION_ROUTE")
public class LocationRoute {
    @EmbeddedId
    private LocationRouteId id;

    @MapsId("routeID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RouteID", nullable = false)
    private Route routeID;

    @MapsId("locationID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LocationID", nullable = false)
    private Location locationID;

    @Column(name = "Stop_Order")
    private Integer stopOrder;

    @Column(name = "arrivalTime")
    private LocalTime arrivalTime;

    @Column(name = "departureTime")
    private LocalTime departureTime;


}