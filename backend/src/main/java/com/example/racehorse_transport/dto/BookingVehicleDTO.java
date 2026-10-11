package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.time.Instant;
import com.example.racehorse_transport.entity.BookingVehicleId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookingVehicleDTO implements Serializable {
    private BookingVehicleId id;
    private Integer bookingID;
    private Integer vehicleID;
    private Instant assignAt;
}
