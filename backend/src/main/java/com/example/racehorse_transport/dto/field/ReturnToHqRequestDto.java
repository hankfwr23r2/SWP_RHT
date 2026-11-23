package com.example.racehorse_transport.dto.field;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReturnToHqRequest")
public class ReturnToHqRequestDto {

    private Integer vehicleId;

    private String driverUsername;

    private String proofImageUrl; // Ảnh chụp khi xe về đến cổng Trụ sở chính Việt Nam

    private String vehicleCondition; // GOOD (bình thường), NEEDS_MAINTENANCE (cần bảo dưỡng đại tu)

    private String note;
}
