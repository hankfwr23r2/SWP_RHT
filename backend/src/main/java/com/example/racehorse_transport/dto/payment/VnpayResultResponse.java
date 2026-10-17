package com.example.racehorse_transport.dto.payment;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnpayResultResponse {
    private boolean success;
    private String rspCode; // Mã phản hồi IPN cho VNPAY: 00, 01, 02, 04, 97, 99
    private String message;
    private Integer bookingId;
    private String bookingCode;
    private String paymentType;
    private String paymentStatus;
    private BigDecimal amount;
    private String transactionCode;
    private String gatewayTransactionNo;
    private String bankCode;
}
