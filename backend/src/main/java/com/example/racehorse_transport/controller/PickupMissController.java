package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.operation.PickupMissChoiceRequestDto;
import com.example.racehorse_transport.dto.operation.PickupMissLiabilityRequestDto;
import com.example.racehorse_transport.dto.operation.PickupMissResponseDto;
import com.example.racehorse_transport.dto.operation.CompletePickupAddOnRequestDto;
import com.example.racehorse_transport.dto.operation.PickupAddOnResponseDto;
import com.example.racehorse_transport.service.PickupMissService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PickupMissController {
    private final PickupMissService misses;

    @GetMapping("/bookings/{id}/pickup-misses")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<PickupMissResponseDto>> listPickupMisses(@PathVariable Long id, Authentication actor) {
        return ApiResponseDto.success(misses.list(id, actor.getName()), "Danh sách đón hụt.");
    }

    @PostMapping("/booking-horses/{id}/pickup-choices")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'COORDINATOR')")
    public ApiResponseDto<PickupMissResponseDto> chooseAfterPickupMiss(@PathVariable Long id,
            @Valid @RequestBody PickupMissChoiceRequestDto request, Authentication actor) {
        return ApiResponseDto.success(misses.decide(id, request, actor.getName()), "Đã ghi lựa chọn.");
    }

    @PostMapping("/booking-horses/{id}/confirm-pickup-liability")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<PickupMissResponseDto> confirmPickupLiability(@PathVariable Long id,
            @Valid @RequestBody PickupMissLiabilityRequestDto request, Authentication actor) {
        return ApiResponseDto.success(misses.assignLiability(id, request, actor.getName()), "Đã chốt bên chịu.");
    }

    @GetMapping("/booking-horses/{id}/pickup-add-ons")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<List<PickupAddOnResponseDto>> pickupAddOns(@PathVariable Long id, Authentication actor) {
        return ApiResponseDto.success(misses.addOns(id, actor.getName()), "Dịch vụ theo ngựa.");
    }

    @PostMapping("/booking-horses/{id}/complete-pickup-add-on")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<PickupAddOnResponseDto> completePickupAddOn(@PathVariable Long id,
            @Valid @RequestBody CompletePickupAddOnRequestDto request, Authentication actor) {
        return ApiResponseDto.success(misses.completeAddOn(id, request, actor.getName()), "Đã ghi dịch vụ hoàn thành.");
    }
}
