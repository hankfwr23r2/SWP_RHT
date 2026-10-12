package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDetailsPayload {
    private List<BookingHorseDto> horses;
    private List<VehicleTripDto> trips;
    private Object route;
    private Object plan;
    private String gate;
    private Object quote;
    private Object payment;
    private Object medical;
    private Object clearance;
    private Object settlement;
    private Object arrival;
    private Object handover;
    private Object rating;
    private Object intake;
    private List<Map<String, Object>> history;
}
