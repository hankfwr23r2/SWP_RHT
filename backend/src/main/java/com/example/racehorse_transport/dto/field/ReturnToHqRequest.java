package com.example.racehorse_transport.dto.field;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnToHqRequest {

    private Integer vehicleId;

    private String driverUsername;

    private String proofImageUrl; // Ảnh chụp khi xe về đến cổng Trụ sở chính Việt Nam

    private String vehicleCondition; // GOOD (bình thường), NEEDS_MAINTENANCE (cần bảo dưỡng đại tu)

    private String note;
}
