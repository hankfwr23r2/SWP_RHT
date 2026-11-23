package com.example.racehorse_transport.dto.fleet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VehicleResponse")
public class VehicleResponseDto {
    private Integer id;
    private String code;
    private String name;
    private String type;
    private String plate;
    private Integer capacity;
    private String status;
    private String homeDepot;
    private String currentLocation;
    private boolean isAvailable; // JSON: "available"
    private String busyReason;
    private String busyBookingCode; // đơn gây khóa xe (nếu có)
    private Long busyUntil; // epoch ms: hết bận dự kiến (cuối khoảng giữ hoặc giờ về trụ sở)
    private boolean onTrip; // đang lăn bánh hoặc chưa xác nhận về trụ sở
    private String onTripBookingCode;
    private Long returnEta; // epoch ms, giờ về trụ sở dự kiến (nếu đã giao)
    @Builder.Default
    private java.util.List<VehicleScheduleDto> upcoming = new java.util.ArrayList<>(); // đơn đã nhận, chưa lăn bánh
    @Builder.Default
    private java.util.List<VehicleScheduleDto> schedule = new java.util.ArrayList<>(); // mọi đơn đang giữ xe (kể cả đang chạy), theo thời gian
}
