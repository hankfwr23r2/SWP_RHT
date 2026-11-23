package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.RouteStopType;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Điểm dừng trên tuyến, thứ tự theo seq.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "route_stop")
public class RouteStop {

    @EmbeddedId
    private RouteStopId id;

    @MapsId("routeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id")
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", length = 30, nullable = false)
    private RouteStopType stopType;

    // Số phút tính từ lúc xuất phát
    @Column(name = "offset_minutes", nullable = false)
    private Integer offsetMinutes;

    @Column(name = "dwell_minutes", nullable = false)
    private Integer dwellMinutes;
}
