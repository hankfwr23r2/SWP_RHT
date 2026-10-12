package com.example.racehorse_transport.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPaymentRequest {

    @NotBlank(message = "Mã đơn hàng không được để trống!")
    private String bookingId;

    private BigDecimal amount;

    private String paymentType; // DEPOSIT, BALANCE, PENALTY, REFUND, SETTLEMENT

    private String paymentMethod; // BANK_TRANSFER, VNPAY, CARD, CASH

    private String transactionCode;

    private String note;
}
