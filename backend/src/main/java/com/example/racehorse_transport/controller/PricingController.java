package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.pricing.PricingPreviewRequestDto;
import com.example.racehorse_transport.dto.pricing.PricingPreviewResponseDto;
import com.example.racehorse_transport.dto.pricing.TransferOptionsRequestDto;
import com.example.racehorse_transport.dto.pricing.TransferOptionsResponseDto;
import com.example.racehorse_transport.service.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pricing")
@RequiredArgsConstructor
public class PricingController {
    private final PricingService service;

    @PostMapping("/preview")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<PricingPreviewResponseDto> preview(
            @Valid @RequestBody PricingPreviewRequestDto request, Authentication actor) {
        return ApiResponseDto.success(service.preview(request, actor.getName()), "Thành công.");
    }

    @PostMapping("/transfer-options")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<TransferOptionsResponseDto> transferOptions(
            @Valid @RequestBody TransferOptionsRequestDto request, Authentication actor) {
        return ApiResponseDto.success(service.transferOptions(request, actor.getName()), "Thành công.");
    }
}
