package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.DeliveryChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings/{id}/delivery-change")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class DeliveryChangeController {
    private final DeliveryChangeService changes;

    @PostMapping("/preview")
    public ApiResponseDto<DeliveryChangeResponseDto> preview(@PathVariable Long id, Authentication authentication,
            @Valid @RequestBody DeliveryChangeRequestDto request) {
        return ApiResponseDto.success(changes.preview(id, authentication.getName(), request), "Đã tính lại cước giao.");
    }

    @PostMapping
    public ApiResponseDto<DeliveryChangeResponseDto> confirm(@PathVariable Long id, Authentication authentication,
            @Valid @RequestBody DeliveryChangeConfirmRequestDto request) {
        return ApiResponseDto.success(changes.confirm(id, authentication.getName(), request), "Đã đổi nơi giao.");
    }
}
