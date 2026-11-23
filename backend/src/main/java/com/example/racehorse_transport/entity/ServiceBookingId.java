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
public class ServiceBookingId implements Serializable {
    @Serial
    private static final long serialVersionUID = 4598102760095405600L;
    @NotNull
    @Column(name = "BookingID", nullable = false)
    private Integer bookingID;

    @NotNull
    @Column(name = "HorseID", nullable = false)
    private Integer horseID;

    @NotNull
    @Column(name = "ServiceID", nullable = false)
    private Integer serviceID;


}