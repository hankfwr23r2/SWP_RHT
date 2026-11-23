package com.example.racehorse_transport.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PaymentResponse")
public class PaymentResponseDto {
    private Integer id;
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
