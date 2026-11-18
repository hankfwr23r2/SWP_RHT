package com.example.racehorse_transport.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CancelBookingRequest")
public class CancelBookingRequestDto {
    private String reason;
}
