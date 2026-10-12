package com.example.racehorse_transport.dto.settlement;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaySettlementRequest {
    private BigDecimal amount;
    private String paymentMethod; // "BANK_TRANSFER", "VNPAY", "CARD", "CASH"
    private String transactionCode;
    private RatingDto rating; // Đánh giá 1-5 sao chuyến đi
}
