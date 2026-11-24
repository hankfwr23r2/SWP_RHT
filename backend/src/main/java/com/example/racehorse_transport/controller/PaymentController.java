package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.payment.*;
import com.example.racehorse_transport.service.PaymentCheckoutService;
import com.example.racehorse_transport.service.PaymentResultService;
import com.example.racehorse_transport.service.PaymentService;
import com.example.racehorse_transport.util.VnpayUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService payments;
    private final PaymentCheckoutService checkout;
    private final PaymentResultService results;

    @GetMapping("/bookings/{bookingId}/payments")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<List<PaymentResponseDto>> payments(@PathVariable Long bookingId, Authentication actor) {
        return ApiResponseDto.success(payments.payments(bookingId, actor.getName()), "Thành công.");
    }

    @PostMapping("/payments/{id}/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<PaymentCheckoutResponseDto> checkout(@PathVariable Long id, Authentication actor,
                                                               HttpServletRequest request) {
        return ApiResponseDto.success(checkout.checkout(id, actor.getName(), VnpayUtil.clientIp(request)), "Thành công.");
    }

    @GetMapping("/payments/vnpay/ipn")
    @PreAuthorize("permitAll()")
    public VnpayIpnResponseDto ipn(@RequestParam Map<String, String> parameters) {
        try {
            return results.receive(parameters);
        } catch (RuntimeException ex) {
            // VNPAY đọc RspCode và gửi lại; lỗi nội bộ trả 99 theo tài liệu của cổng
            log.error("Không xử lý được IPN VNPAY {}", parameters.get("vnp_TxnRef"), ex);
            return new VnpayIpnResponseDto("99", "Unknown error");
        }
    }

    @GetMapping("/payments/vnpay/return")
    @PreAuthorize("permitAll()")
    public ApiResponseDto<PaymentResultResponseDto> paymentReturn(@RequestParam Map<String, String> parameters) {
        return ApiResponseDto.success(results.receiveReturn(parameters), "Đã kiểm tra kết quả VNPAY.");
    }
}
