package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class BookingVehicleId implements Serializable {
    @Serial
    private static final long serialVersionUID = 8184741758710053012L;
    @NotNull
    @Column(name = "BookingID", nullable = false)
    private Integer bookingID;

    @NotNull
    @Column(name = "VehicleID", nullable = false)
    private Integer vehicleID;


}