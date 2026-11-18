package com.example.racehorse_transport.dto.incident;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ApproveIncidentPlanRequest")
public class ApproveIncidentPlanRequestDto {

    private String managerUsername;

    private String directive; // Ý kiến chỉ đạo phê duyệt

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal approvedCost;

    private String costBearer; // COMPANY, CUSTOMER
}
