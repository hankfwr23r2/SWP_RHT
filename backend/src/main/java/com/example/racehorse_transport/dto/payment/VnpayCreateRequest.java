package com.example.racehorse_transport.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnpayCreateRequest {

    @NotBlank(message = "Mã đơn hàng không được để trống!")
    private String bookingId;

    @NotBlank(message = "Loại thanh toán không được để trống!")
    private String paymentType; // DEPOSIT, BALANCE, SETTLEMENT
}
