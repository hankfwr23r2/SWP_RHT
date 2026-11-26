package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.quotation.*;
import com.example.racehorse_transport.service.QuotationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class QuotationController {
    private final QuotationService service;

    @GetMapping("/bookings/{bookingId}/quotations")
    @PreAuthorize("hasAnyRole('MANAGER','CUSTOMER')")
    public ApiResponseDto<List<QuotationResponseDto>> quotations(@PathVariable Long bookingId, Authentication actor) {
        boolean manager = actor.getAuthorities().stream().anyMatch(role -> "ROLE_MANAGER".equals(role.getAuthority()));
        return ApiResponseDto.success(service.quotations(bookingId, actor.getName(), manager), "Thành công.");
    }

    @PostMapping("/bookings/{bookingId}/quotations")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<QuotationResponseDto> draft(@PathVariable Long bookingId) {
        return ApiResponseDto.success(service.createDraft(bookingId), "Đã lập bản nháp.");
    }

    @PostMapping("/quotations/{id}/adjust")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<QuotationResponseDto> adjust(@PathVariable Long id,
            @Valid @RequestBody QuotationAdjustmentRequestDto request) {
        return ApiResponseDto.success(service.adjust(id, request), "Đã thêm điều chỉnh.");
    }

    @PostMapping("/quotations/{id}/send")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<QuotationResponseDto> send(@PathVariable Long id, Authentication actor,
            @Valid @RequestBody(required = false) QuotationSendRequestDto request) {
        return ApiResponseDto.success(service.send(id, actor.getName()), "Đã gửi báo giá.");
    }

    @PostMapping("/quotations/{id}/accept")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<QuotationResponseDto> accept(@PathVariable Long id, Authentication actor,
            @Valid @RequestBody(required = false) QuotationAcceptRequestDto request) {
        return ApiResponseDto.success(service.accept(id, actor.getName()), "Đã chấp nhận báo giá.");
    }

    @PostMapping("/quotations/{id}/reject")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<QuotationResponseDto> reject(@PathVariable Long id,
            @Valid @RequestBody QuotationRejectRequestDto request, Authentication actor) {
        return ApiResponseDto.success(service.reject(id, request, actor.getName()), "Đã từ chối báo giá.");
    }
}
