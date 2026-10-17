package com.example.racehorse_transport.dto.payment;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnpayCreateResponse {
    private String paymentUrl;
    private String transactionCode;
    private BigDecimal amount;
    private Long expiresAt; // epoch millis, hết hạn link thanh toán
}
