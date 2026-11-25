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
    private Long departureScheduleId;
    private Long routeId;
    private String routeCode;
    private String routeName;
    private String originCode;
    private String destinationCode;
    private LocalDate departureDate;
    private Instant plannedDepartureAt;
    private Instant revisedDepartureAt;
    private Instant delayedAt;
    private String delayReason;
    private Instant bookingCutoffAt;
    private Integer stallCapacity;
    private Integer bookedStallCount;
    private Integer availableStalls;
    private DepartureStatus status;
    // Quản lý đã quyết định chạy: điều phối mới lập được lượt trục
    private boolean runConfirmed;

    // Service đã nạp tuyến kèm điểm đầu, điểm cuối; số khoang còn trống do DepartureCapacityPolicy tính
    public static DepartureResponseDto from(Departure departure, int availableStalls) {
        return DepartureResponseDto.builder()
                .id(departure.getId())
                .departureScheduleId(departure.getDepartureSchedule() == null ? null : departure.getDepartureSchedule().getId())
                .routeId(departure.getRoute().getId())
                .routeCode(departure.getRoute().getCode())
                .routeName(departure.getRoute().getName())
                .originCode(departure.getRoute().getOrigin().getCode())
                .destinationCode(departure.getRoute().getDestination().getCode())
                .departureDate(departure.getDepartureDate())
                .plannedDepartureAt(departure.getPlannedDepartureAt())
                .revisedDepartureAt(departure.getRevisedDepartureAt())
                .delayedAt(departure.getDelayedAt())
                .delayReason(departure.getDelayReason())
                .bookingCutoffAt(departure.getBookingCutoffAt())
                .stallCapacity(departure.getStallCapacity())
                .bookedStallCount(departure.getBookedStallCount())
                .availableStalls(availableStalls)
                .status(departure.getStatus())
                .runConfirmed(departure.getRunConfirmedAt() != null)
                .build();
    }
}
