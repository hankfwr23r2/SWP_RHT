package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(name = "DepartureResponse")
public class DepartureResponseDto {

    private Long id;
    // NULL = chuyến đột xuất
    private Long scheduleId;
    private Long routeId;
    private String routeCode;
    private String routeName;
    private String originCode;
    private String destinationCode;
    private LocalDate departureDate;
    private Instant plannedDepartAt;
    private Instant bookingCutoffAt;
    private Integer capacityStalls;
    private Integer bookedStalls;
    private Integer availableStalls;
    private DepartureStatus status;

    // Service đã nạp tuyến kèm điểm đầu, điểm cuối; số khoang còn trống do DepartureCapacityPolicy tính
    public static DepartureResponseDto from(Departure departure, int availableStalls) {
        return DepartureResponseDto.builder()
                .id(departure.getId())
                .scheduleId(departure.getSchedule() == null ? null : departure.getSchedule().getId())
                .routeId(departure.getRoute().getId())
                .routeCode(departure.getRoute().getCode())
                .routeName(departure.getRoute().getName())
                .originCode(departure.getRoute().getOrigin().getCode())
                .destinationCode(departure.getRoute().getDestination().getCode())
                .departureDate(departure.getDepartureDate())
                .plannedDepartAt(departure.getPlannedDepartAt())
                .bookingCutoffAt(departure.getBookingCutoffAt())
                .capacityStalls(departure.getCapacityStalls())
                .bookedStalls(departure.getBookedStalls())
                .availableStalls(availableStalls)
                .status(departure.getStatus())
                .build();
    }
}
