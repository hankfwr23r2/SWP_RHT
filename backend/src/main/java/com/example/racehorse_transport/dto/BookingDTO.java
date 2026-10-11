package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookingDTO implements Serializable {
    private Integer id;
    private Integer customerID;
    private Integer routeID;
    private Instant bookingDate;
    private Instant departureDate;
    private String status;
}
