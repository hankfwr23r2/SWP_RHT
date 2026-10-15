package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposeExternalPlanRequest {

    private String resolutionType; // EXTERNAL_PARTNER, REROUTE, FORCE_MAJEURE

    private String partnerName; // Tên đơn vị cứu hộ / phòng khám thú y ngoài

    private String partnerService; // Cẩu kéo sửa chữa, Khám cấp cứu thú y, Thuê chuồng đệm

    private String partnerPhone;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal estimatedCost;

    private String costBearer; // COMPANY (Nhà xe chịu), CUSTOMER (Khách chịu)

    private String description;

    private Map<String, Object> plan;

    private String coordinatorUsername;
}
