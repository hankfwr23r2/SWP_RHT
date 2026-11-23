package com.example.racehorse_transport.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PaymentSummaryResponse")
public class PaymentSummaryResponseDto {
    private String bookingCode;
    private String waybillNo;
    private String bookingStatus;

    private BigDecimal totalAmount;
    private BigDecimal depositAmount; // 30%
    private BigDecimal balanceAmount; // 70%

    private BigDecimal totalPaid;
    private BigDecimal remainingBalance;

    private boolean depositPaid;
    private boolean balancePaid;
    private boolean fullyPaid;

    private List<PaymentResponseDto> payments;
}
