package com.example.racehorse_transport.dto.field;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaySettlementRequest {

    private BigDecimal amount;

    private String paymentMethod; // BANK_TRANSFER, VNPAY, CASH

    private String transactionRef;

    private String note;
}
