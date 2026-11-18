package com.example.racehorse_transport.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CancelWithRefundResponse")
public class CancelWithRefundResponseDto {
    private String bookingCode;
    private String bookingStatus;
    private BigDecimal depositPaid;
    private long daysToDeparture;
    private int refundPercentage;
    private BigDecimal refundAmount;
    private BigDecimal penaltyAmount;
    private String message;
    private PaymentResponseDto refundPayment;
}
