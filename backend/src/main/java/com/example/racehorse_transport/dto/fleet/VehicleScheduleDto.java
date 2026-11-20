package com.example.racehorse_transport.dto.fleet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/** Một đơn chưa lăn bánh đã nhận xe: dùng để hiển thị lịch, không tự khóa xe cho ngày khác. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VehicleSchedule")
public class VehicleScheduleDto {
    private String bookingCode;
    private String tripType; // domestic, international
    private Long departAt; // epoch ms
    private Long busyFrom; // epoch ms, đầu khoảng giữ xe (gồm chuẩn bị)
    private Long busyUntil; // epoch ms, cuối khoảng giữ xe (gồm quay về trụ sở)
    private boolean onTrip; // xe đã rời trụ sở cho đơn này, chưa xác nhận về
    private Long returnEta; // epoch ms, giờ về trụ sở dự kiến (đã giao, đang về)
    private String driverName; // kíp của chuyến dùng xe này; null khi chưa chốt phương án
    private String escortName;
}
