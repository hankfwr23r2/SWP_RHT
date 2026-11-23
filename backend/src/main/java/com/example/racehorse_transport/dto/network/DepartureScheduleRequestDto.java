package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.DepartureSchedule;
import com.example.racehorse_transport.entity.Route;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

// Lịch chạy định kỳ của một tuyến. Đổi lịch không làm đổi các chuyến đã sinh (sức chứa đã chép vào chuyến).
@Getter
@Setter
@Schema(name = "DepartureScheduleRequest")
public class DepartureScheduleRequestDto {

    @NotNull(message = "Chọn tuyến")
    private Long routeId;

    @NotNull(message = "Nhập giờ xuất phát")
    private LocalTime departureTime;

    @NotNull(message = "Nhập số giờ chốt nhận đơn trước giờ xuất phát")
    @Min(value = 1, message = "Giờ chốt nhận đơn phải lớn hơn 0")
    private Integer bookingCutoffHours;

    @NotNull(message = "Nhập số khoang tối đa mỗi chuyến")
    @Min(value = 1, message = "Số khoang phải lớn hơn 0")
    private Integer capacityStalls;

    @NotNull(message = "Nhập ngày bắt đầu hiệu lực")
    private LocalDate validFrom;

    private LocalDate validTo;

    // Thứ trong tuần theo ISO: 1 = thứ Hai, 7 = Chủ nhật
    @NotEmpty(message = "Chọn ít nhất một ngày chạy trong tuần")
    private Set<@NotNull @Min(value = 1, message = "Thứ trong tuần từ 1 đến 7") @Max(value = 7, message = "Thứ trong tuần từ 1 đến 7") Short> weekdays;

    // Lịch mới, chưa lưu. Tuyến do service nạp; trạng thái do service đặt. Các ngày chạy do service ghi riêng.
    public DepartureSchedule toEntity(Route route) {
        DepartureSchedule schedule = new DepartureSchedule();
        applyTo(schedule, route);
        return schedule;
    }

    public void applyTo(DepartureSchedule schedule, Route route) {
        schedule.setRoute(route);
        schedule.setDepartureTime(departureTime);
        schedule.setBookingCutoffHours(bookingCutoffHours);
        schedule.setCapacityStalls(capacityStalls);
        schedule.setValidFrom(validFrom);
        schedule.setValidTo(validTo);
    }
}
