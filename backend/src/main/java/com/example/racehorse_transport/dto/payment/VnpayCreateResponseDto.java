package com.example.racehorse_transport.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VnpayCreateResponse")
public class VnpayCreateResponseDto {
    private String paymentUrl;
    private String transactionCode;
    private BigDecimal amount;
    private Long expiresAt; // epoch millis, hết hạn link thanh toán
}
