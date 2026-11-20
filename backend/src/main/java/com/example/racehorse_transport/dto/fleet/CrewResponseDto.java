package com.example.racehorse_transport.dto.fleet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CrewResponse")
public class CrewResponseDto {
    private Integer userId;
    private String staffCode;
    private String name;
    private String role; // DRIVER, ESCORT, COORDINATOR, SPECIALIST
    private String phone;
    private String email;
    private String employmentStatus; // ACTIVE, LOCKED
    private boolean isAvailable; // JSON: "available"
    private String busyReason;
    private String busyBookingCode; // đơn gây khóa nhân sự (nếu có)
    private Long busyUntil; // epoch ms: cuối khoảng giữ hoặc giờ về trụ sở dự kiến
}
