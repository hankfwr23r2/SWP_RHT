package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings/{id}/documents")
@RequiredArgsConstructor
public class BookingDocumentReviewController {
    private final DocumentReviewService documents;

    @GetMapping
    @PreAuthorize("hasAnyRole('SPECIALIST','MANAGER','COORDINATOR')")
    public ApiResponseDto<List<BookingDocumentResponseDto>> list(@PathVariable Long id) {
        return ApiResponseDto.success(documents.list(id), "Thành công.");
    }

    @GetMapping("/checklist")
    @PreAuthorize("hasAnyRole('SPECIALIST','MANAGER','COORDINATOR')")
    public ApiResponseDto<BookingDocumentChecklistResponseDto> checklist(@PathVariable Long id) {
        return ApiResponseDto.success(documents.checklist(id, null), "Yêu cầu giấy tờ.");
    }

    @GetMapping("/my-checklist")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<BookingDocumentChecklistResponseDto> customerChecklist(@PathVariable Long id,
            Authentication authentication) {
        return ApiResponseDto.success(documents.checklist(id, authentication.getName()), "Yêu cầu giấy tờ.");
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<BookingDocumentResponseDto> upload(@PathVariable Long id, Authentication authentication, @Valid @ModelAttribute DocumentUploadRequestDto request) {
        return ApiResponseDto.success(documents.attach(id, authentication.getName(), request), "Thành công.");
    }

    @PostMapping("/upload-for-partner")
    @PreAuthorize("hasRole('SPECIALIST')")
    public ApiResponseDto<BookingDocumentResponseDto> uploadForPartner(@PathVariable Long id,
            Authentication authentication, @Valid @ModelAttribute PartnerDocumentUploadRequestDto request) {
        return ApiResponseDto.success(documents.attachForPartner(id, authentication.getName(), request), "Thành công.");
    }

    @PostMapping("/{documentId}/approve")
    @PreAuthorize("hasRole('SPECIALIST')")
    public ApiResponseDto<BookingDocumentResponseDto> approve(@PathVariable Long id, @PathVariable Long documentId, Authentication authentication,
            @Valid @RequestBody(required = false) DocumentApprovalRequestDto request) {
        return ApiResponseDto.success(documents.approve(id, documentId, authentication.getName(), request), "Thành công.");
    }

    @PostMapping("/{documentId}/reject")
    @PreAuthorize("hasRole('SPECIALIST')")
    public ApiResponseDto<BookingDocumentResponseDto> reject(@PathVariable Long id, @PathVariable Long documentId, Authentication authentication, @Valid @RequestBody DocumentRejectionRequestDto request) {
        return ApiResponseDto.success(documents.reject(id, documentId, authentication.getName(), request.reason()), "Thành công.");
    }
}
