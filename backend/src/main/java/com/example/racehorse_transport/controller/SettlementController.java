package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.payment.SettlementResponseDto;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.dto.payment.ExtraChargeRequestDto;
import jakarta.validation.Valid;
import com.example.racehorse_transport.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings/{bookingId}/settlement")
@PreAuthorize("hasRole('MANAGER')")
public class SettlementController {
    private final SettlementService settlements;

    @GetMapping
    public ApiResponseDto<SettlementResponseDto> settlement(@PathVariable Long bookingId) {
        return ApiResponseDto.success(settlements.settlement(bookingId), "Thành công.");
    }

    @PostMapping("/extra-charges")
    public ApiResponseDto<PaymentResponseDto> createExtraCharge(@PathVariable Long bookingId, Authentication actor,
            @Valid @RequestBody ExtraChargeRequestDto request) {
        return ApiResponseDto.success(settlements.createExtraCharge(bookingId, request.costItemIds(), actor.getName()),
                "Đã lập khoản thu thêm.");
    }

    @PostMapping("/complete")
    public ApiResponseDto<SettlementResponseDto> complete(@PathVariable Long bookingId, Authentication actor) {
        return ApiResponseDto.success(settlements.complete(bookingId, actor.getName()), "Đã đóng đơn.");
    }
}
