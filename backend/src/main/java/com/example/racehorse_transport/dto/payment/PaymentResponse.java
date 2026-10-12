package com.example.racehorse_transport.dto.payment;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Integer id;
    private Integer bookingId;
    private String bookingCode;
    private String waybillNo;
    private BigDecimal amount;
    private Instant paymentDate;
    private String paymentType;
    private String paymentMethod;
    private String transactionCode;
    private String status;
    private String note;
}
