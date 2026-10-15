package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveIncidentPlanRequest {

    private String managerUsername;

    private String directive; // Ý kiến chỉ đạo phê duyệt

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal approvedCost;

    private String costBearer; // COMPANY, CUSTOMER
}
