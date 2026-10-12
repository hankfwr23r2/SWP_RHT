package com.example.racehorse_transport.dto.payment;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelWithRefundResponse {
    private Integer bookingId;
    private String bookingCode;
    private String bookingStatus;
    private BigDecimal depositPaid;
    private long daysToDeparture;
    private int refundPercentage;
    private BigDecimal refundAmount;
    private BigDecimal penaltyAmount;
    private String message;
    private PaymentResponse refundPayment;
}
