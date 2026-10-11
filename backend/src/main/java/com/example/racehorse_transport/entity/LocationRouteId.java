package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class LocationRouteId implements Serializable {
    @Serial
    private static final long serialVersionUID = 7193089408335457698L;
    @NotNull
    @Column(name = "RouteID", nullable = false)
    private Integer routeID;

    @NotNull
    @Column(name = "LocationID", nullable = false)
    private Integer locationID;


}