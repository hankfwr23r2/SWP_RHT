package com.example.racehorse_transport.dto.incident;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.racehorse_transport.entity.Incident;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ProposeExternalPlanRequest")
public class ProposeExternalPlanRequestDto {

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

    public void applyTo(Incident incident) {
        incident.setResolutionType(getResolutionType() != null
                ? getResolutionType().toUpperCase(Locale.ROOT)
                : "EXTERNAL_PARTNER");
        incident.setPartnerName(getPartnerName());
        incident.setPartnerService(getPartnerService());
        incident.setPartnerPhone(getPartnerPhone());
        incident.setEstimatedCost(getEstimatedCost());
    }
}
