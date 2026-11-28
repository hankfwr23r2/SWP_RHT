package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/bookings/{id}/insurances")
@RequiredArgsConstructor
public class BookingInsuranceReviewController {
    private final InsuranceService insurance;

    @PostMapping("/{insuranceId}/record-evidence")
    @PreAuthorize("hasRole('SPECIALIST')")
    public ApiResponseDto<Void> recordEvidence(@PathVariable Long id, @PathVariable Long insuranceId, @Valid @RequestBody InsuranceEvidenceRequestDto request, Authentication actor) {
        insurance.recordEvidence(id, insuranceId, request, actor.getName());
        return ApiResponseDto.success(null, "Đã cập nhật bảo hiểm.");
    }

    @PostMapping("/{insuranceId}/confirm")
    @PreAuthorize("hasRole('SPECIALIST')")
    public ApiResponseDto<Void> confirm(@PathVariable Long id, @PathVariable Long insuranceId, Authentication authentication,
            @Valid @RequestBody(required = false) InsuranceConfirmationRequestDto request) {
        insurance.confirm(id, insuranceId, authentication.getName());
        return ApiResponseDto.success(null, "Đã cập nhật bảo hiểm.");
    }

    @PostMapping("/{insuranceId}/record-premium-refund")
    @PreAuthorize("hasRole('SPECIALIST')")
    public ApiResponseDto<Void> recordPremiumRefund(@PathVariable Long id, @PathVariable Long insuranceId,
            @Valid @RequestBody InsurancePremiumRefundRequestDto request, Authentication authentication) {
        insurance.recordPremiumRefund(id, insuranceId, request, authentication.getName());
        return ApiResponseDto.success(null, "Đã ghi phí bảo hiểm được hoàn.");
    }

    @PostMapping("/{insuranceId}/request-revision")
    @PreAuthorize("hasRole('SPECIALIST')")
    public ApiResponseDto<Void> requestRevision(@PathVariable Long id, @PathVariable Long insuranceId, Authentication authentication, @Valid @RequestBody InsuranceRevisionReasonRequestDto request) {
        insurance.requestRevision(id, insuranceId, authentication.getName(), request.reason());
        return ApiResponseDto.success(null, "Đã cập nhật bảo hiểm.");
    }

    @PostMapping("/{insuranceId}/reopen")
    @PreAuthorize("hasRole('SPECIALIST')")
    public ApiResponseDto<Void> reopen(@PathVariable Long id, @PathVariable Long insuranceId,
            Authentication authentication, @Valid @RequestBody InsuranceReopenRequestDto request) {
        insurance.reopen(id, insuranceId, authentication.getName(), request.reason());
        return ApiResponseDto.success(null, "Đã mở lại bảo hiểm.");
    }

    @PostMapping(value = "/{insuranceId}/revise", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<Void> revise(@PathVariable Long id, @PathVariable Long insuranceId, Authentication authentication,
            @Valid @RequestPart("request") PersonalInsuranceRevisionRequestDto request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        insurance.revise(id, insuranceId, authentication.getName(), request, file);
        return ApiResponseDto.success(null, "Đã cập nhật bảo hiểm.");
    }
}
