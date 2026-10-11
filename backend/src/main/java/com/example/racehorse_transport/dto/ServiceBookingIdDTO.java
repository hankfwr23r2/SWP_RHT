package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceBookingIdDTO implements Serializable {
    private Integer bookingID;
    private Integer horseID;
    private Integer serviceID;
}
