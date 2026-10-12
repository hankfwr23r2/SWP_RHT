package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportIncidentRequest {

    @NotNull(message = "Mã đơn hàng không được để trống")
    private Integer bookingId;

    @NotBlank(message = "Loại sự cố không được để trống")
    private String incidentType; // VEHICLE_BREAKDOWN, HORSE_HEALTH, TRAFFIC_DELAY, OTHER

    @NotBlank(message = "Tiêu đề sự cố không được để trống")
    private String incidentName;

    private String description;

    private String reporterUsername; // Driver hoặc Escort
}
