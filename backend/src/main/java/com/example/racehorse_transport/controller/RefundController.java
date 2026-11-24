package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.dto.payment.RefundResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.payment.MarkRefundedRequestDto;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.dto.payment.RefundQuotationLinesRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import java.util.List;
import com.example.racehorse_transport.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class RefundController {
    private final RefundService refunds;

    @GetMapping("/refunds")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<List<RefundResponseDto>> refunds(@RequestParam(defaultValue = "PENDING") PaymentStatus status) {
        return ApiResponseDto.success(refunds.refunds(status), "Thành công.");
    }

    @GetMapping("/bookings/{id}/refund-review")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<BookingCancellationResponseDto> review(@PathVariable Long id) {
        return ApiResponseDto.success(refunds.reviewCancelledLines(id), "Thành công.");
    }

    @PostMapping("/bookings/{id}/refund-unspent-lines")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<List<PaymentResponseDto>> refundUnspentLines(@PathVariable Long id,
            @Valid @RequestBody RefundQuotationLinesRequestDto request) {
        return ApiResponseDto.success(refunds.refundUnspentLines(id, request.unspentQuotationLineIds()), "Đã lập khoản hoàn.");
    }

    @PostMapping(path = "/refunds/{id}/mark-refunded", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<PaymentResponseDto> markRefunded(@PathVariable Long id, Authentication actor,
            @Valid @RequestPart("request") MarkRefundedRequestDto request,
            @RequestPart(value = "receipt", required = false) MultipartFile receipt) {
        return ApiResponseDto.success(refunds.markRefunded(id, actor.getName(), request, receipt), "Đã ghi nhận hoàn tiền.");
    }
}
