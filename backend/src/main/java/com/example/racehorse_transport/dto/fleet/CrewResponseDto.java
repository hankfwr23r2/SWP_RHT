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
    private boolean isAvailable;
    private String busyReason;
}
