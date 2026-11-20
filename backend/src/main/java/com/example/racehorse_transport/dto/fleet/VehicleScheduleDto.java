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
}
