package com.example.racehorse_transport.dto.payment;

import com.example.racehorse_transport.dto.settlement.RatingDto;
import jakarta.validation.Valid;
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

    // Đánh giá chuyến đi gửi kèm khi thanh toán quyết toán, chỉ ghi nhận khi VNPAY xác nhận đã nhận tiền
    @Valid
    private RatingDto rating;
}
