package com.example.racehorse_transport.dto.field;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryArriveRequest {
    private String driverUsername;
    private String proofImageUrl; // Ảnh chụp tại cổng điểm giao
    private String note;
}
