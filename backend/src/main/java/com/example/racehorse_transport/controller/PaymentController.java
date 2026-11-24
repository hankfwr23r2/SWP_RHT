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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
        return results.receive(parameters);
    }

    @GetMapping("/payments/vnpay/return")
    @PreAuthorize("permitAll()")
    public ApiResponseDto<PaymentResultResponseDto> paymentReturn(@RequestParam Map<String, String> parameters) {
        return ApiResponseDto.success(results.receiveReturn(parameters), "Đã kiểm tra kết quả VNPAY.");
    }
}
