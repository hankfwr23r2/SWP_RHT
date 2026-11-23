package com.example.racehorse_transport.dto.incident;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ResolveIncidentRequest")
public class ResolveIncidentRequestDto {

    private String driverUsername;

    private BigDecimal actualCost; // Số tiền thực tế theo hóa đơn của đội ngoài

    private String invoiceUrl; // Ảnh chụp hóa đơn đỏ / biên lai / đơn thuốc

    private String note;
}
