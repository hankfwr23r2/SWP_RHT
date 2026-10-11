package com.example.racehorse_transport.dto.fleet;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrewResponse {
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
