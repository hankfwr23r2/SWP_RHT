package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Route;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

// Chuyến đột xuất (không theo lịch). Giờ xuất phát, giờ chốt do service tính; trạng thái và số khoang đã đặt do service đặt.
@Getter
@Setter
@Schema(name = "DepartureCreateRequest")
public class DepartureCreateRequestDto {

    @NotNull(message = "Chọn tuyến")
    private Long routeId;

    @NotNull(message = "Nhập ngày chạy")
    private LocalDate departureDate;

    @NotNull(message = "Nhập giờ xuất phát")
    private LocalTime departureTime;

    @NotNull(message = "Nhập số giờ chốt nhận đơn trước giờ xuất phát")
    @Min(value = 1, message = "Giờ chốt nhận đơn phải lớn hơn 0")
    private Integer bookingCutoffHours;

    @NotNull(message = "Nhập số khoang tối đa")
    @Min(value = 1, message = "Số khoang phải lớn hơn 0")
    private Integer stallCapacity;

    public Departure toEntity(Route route, Instant plannedDepartureAt, Instant bookingCutoffAt) {
        Departure departure = new Departure();
        departure.setRoute(route);
        departure.setDepartureDate(departureDate);
        departure.setPlannedDepartureAt(plannedDepartureAt);
        departure.setBookingCutoffAt(bookingCutoffAt);
        departure.setStallCapacity(stallCapacity);
        return departure;
    }
}
