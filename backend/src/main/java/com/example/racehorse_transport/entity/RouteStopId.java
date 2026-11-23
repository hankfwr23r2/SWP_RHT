package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

// Khóa của route_stop.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class RouteStopId implements Serializable {

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;
}
