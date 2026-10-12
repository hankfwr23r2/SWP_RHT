package com.example.racehorse_transport.dto.incident;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveIncidentPlanRequest {

    private String managerUsername;

    private String directive; // Ý kiến chỉ đạo phê duyệt

    private BigDecimal approvedCost;

    private String costBearer; // COMPANY, CUSTOMER
}
