package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import java.time.Instant;
import java.time.LocalDate;

// Một chuyến trên bảng quyết định chạy. Số chỗ đã cọc, mốc và cảnh báo do service tính.
public record DepartureDecisionResponseDto(Long id, String routeCode, String routeName, LocalDate departureDate,
                                           Instant plannedDepartureAt, Instant decisionAt, DepartureStatus status,
                                           int stallCapacity, int bookedStallCount, int committedStallCount,
                                           int uncommittedBookingCount, boolean belowMinimum, boolean confirmable,
                                           Instant runConfirmedAt) {

    public static DepartureDecisionResponseDto from(Departure departure, Instant decisionAt, int committedStallCount,
                                                    int uncommittedBookingCount, boolean belowMinimum, boolean confirmable) {
        return new DepartureDecisionResponseDto(departure.getId(), departure.getRoute().getCode(), departure.getRoute().getName(),
                departure.getDepartureDate(), departure.getPlannedDepartureAt(), decisionAt, departure.getStatus(),
                departure.getStallCapacity(), departure.getBookedStallCount(), committedStallCount, uncommittedBookingCount,
                belowMinimum, confirmable, departure.getRunConfirmedAt());
    }
}
