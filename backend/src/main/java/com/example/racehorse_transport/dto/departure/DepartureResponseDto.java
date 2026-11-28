package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.entity.enums.DepartureAvailability;
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
    private Long coordinatorId;
    private String coordinatorName;
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
    private DepartureAvailability availability;
    private DepartureStatus status;
    // Quản lý đã quyết định chạy: điều phối mới lập được lượt trục
    private boolean runConfirmed;

    // Service đã nạp tuyến kèm điểm đầu, điểm cuối; số khoang còn trống do DepartureCapacityPolicy tính
    public static DepartureResponseDto from(Departure departure, int availableStalls) {
        return from(departure, availableStalls, null);
    }

    public static DepartureResponseDto forCustomer(Departure departure, DepartureAvailability availability) {
        return from(departure, null, availability);
    }

    private static DepartureResponseDto from(Departure departure, Integer availableStalls, DepartureAvailability availability) {
        return DepartureResponseDto.builder()
                .id(departure.getId())
                .coordinatorId(departure.getCoordinator() == null ? null : departure.getCoordinator().getUserId())
                .coordinatorName(departure.getCoordinator() == null ? null : departure.getCoordinator().getFullName())
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
                .stallCapacity(availability == null ? departure.getStallCapacity() : null)
                .bookedStallCount(availability == null ? departure.getBookedStallCount() : null)
                .availability(availability)
                .availableStalls(availableStalls)
                .status(departure.getStatus())
                .runConfirmed(departure.getRunConfirmedAt() != null)
                .build();
    }
}
