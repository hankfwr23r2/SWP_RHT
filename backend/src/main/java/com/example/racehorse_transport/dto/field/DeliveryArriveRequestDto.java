package com.example.racehorse_transport.dto.field;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "DeliveryArriveRequest")
public class DeliveryArriveRequestDto {
    private String driverUsername;
    private String proofImageUrl; // Ảnh chụp tại cổng điểm giao
    private String note;
}
