package com.example.racehorse_transport.dto.booking;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayDepositRequest {
    private String paymentMethod;
    private BigDecimal amount;
    private String transactionCode;
}
