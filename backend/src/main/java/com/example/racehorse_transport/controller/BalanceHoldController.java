package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.operation.BalanceHoldContactRequestDto;
import com.example.racehorse_transport.dto.operation.PickupHubTransferRequestDto;
import com.example.racehorse_transport.dto.operation.PickupWaitExtensionRequestDto;
import com.example.racehorse_transport.service.PickupBalanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings/{bookingId}")
@PreAuthorize("hasRole('COORDINATOR')")
public class BalanceHoldController {
    private final PickupBalanceService balances;

    @PostMapping("/balance-hold-contacts")
    public ApiResponseDto<Void> recordContact(@PathVariable Long bookingId,
            @Valid @RequestBody BalanceHoldContactRequestDto request, Authentication authentication) {
        balances.recordContact(bookingId, request, authentication.getName());
        return ApiResponseDto.success(null, "Đã ghi lần liên hệ.");
    }

    @PostMapping("/extend-pickup-payment-wait")
    public ApiResponseDto<Void> extend(@PathVariable Long bookingId,
            @Valid @RequestBody PickupWaitExtensionRequestDto request, Authentication authentication) {
        balances.extend(bookingId, request, authentication.getName());
        return ApiResponseDto.success(null, "Đã gia hạn chờ thanh toán.");
    }

    @PostMapping("/send-unclaimed-pickup-to-hub")
    public ApiResponseDto<Void> sendToHub(@PathVariable Long bookingId,
            @Valid @RequestBody PickupHubTransferRequestDto request, Authentication authentication) {
        balances.transferToHub(bookingId, request, authentication.getName());
        return ApiResponseDto.success(null, "Đã xác nhận đưa ngựa về hub.");
    }
}
