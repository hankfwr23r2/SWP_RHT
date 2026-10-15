package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "Mã chuyến xe không được để trống")
    @Size(max = 100)
    private String tripId;

    private String description;

    @NotBlank(message = "Ảnh hiện trường không được để trống")
    @Size(max = 500)
    private String photoUrl;

    @NotNull(message = "Vĩ độ vị trí sự cố không được để trống")
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @NotNull(message = "Kinh độ vị trí sự cố không được để trống")
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    private String reporterUsername;
}
