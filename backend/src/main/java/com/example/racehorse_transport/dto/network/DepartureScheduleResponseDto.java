package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.DepartureSchedule;
import com.example.racehorse_transport.entity.DepartureScheduleDay;
import com.example.racehorse_transport.entity.enums.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(name = "DepartureScheduleResponse")
public class DepartureScheduleResponseDto {

    private Long id;
    private Long routeId;
    private String routeCode;
    private LocalTime departureTime;
    private Integer bookingCutoffHours;
    private Long baseVehicleClassId;
    private Integer baseVehicleCount;
    private LocalDate validFrom;
    private LocalDate validTo;
    private ScheduleStatus status;
    private List<Short> daysOfWeek;

    // Service đã nạp route
    public static DepartureScheduleResponseDto from(DepartureSchedule schedule, List<DepartureScheduleDay> days) {
        return DepartureScheduleResponseDto.builder()
                .id(schedule.getId())
                .routeId(schedule.getRoute().getId())
                .routeCode(schedule.getRoute().getCode())
                .departureTime(schedule.getDepartureTime())
                .bookingCutoffHours(schedule.getBookingCutoffHours())
                .baseVehicleClassId(schedule.getBaseVehicleClass().getId())
                .baseVehicleCount(schedule.getBaseVehicleCount())
                .validFrom(schedule.getValidFrom())
                .validTo(schedule.getValidTo())
                .status(schedule.getStatus())
                .daysOfWeek(days.stream().map(day -> day.getId().getDayOfWeek()).sorted().toList())
                .build();
    }
}
