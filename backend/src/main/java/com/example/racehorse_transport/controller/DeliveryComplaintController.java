package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.complaint.ComplaintCreateRequestDto;
import com.example.racehorse_transport.dto.complaint.ComplaintOnBehalfRequestDto;
import com.example.racehorse_transport.dto.complaint.ComplaintReplyRequestDto;
import com.example.racehorse_transport.dto.complaint.ComplaintResolveRequestDto;
import com.example.racehorse_transport.dto.complaint.DeliveryComplaintResponseDto;
import com.example.racehorse_transport.dto.complaint.DeliveryConfirmationRequestDto;
import com.example.racehorse_transport.service.DeliveryComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings/{bookingId}/delivery-complaints")
public class DeliveryComplaintController {

    private final DeliveryComplaintService complaints;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'MANAGER')")
    public ApiResponseDto<DeliveryComplaintResponseDto> view(@PathVariable Long bookingId, Authentication actor) {
        return ApiResponseDto.success(complaints.view(bookingId, actor.getName()), "Thành công.");
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<DeliveryComplaintResponseDto> confirm(@PathVariable Long bookingId,
            @Valid @RequestBody DeliveryConfirmationRequestDto request, Authentication actor) {
        return ApiResponseDto.success(complaints.confirm(bookingId, actor.getName()), "Đã xác nhận nhận ngựa.");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponseDto<DeliveryComplaintResponseDto> submit(@PathVariable Long bookingId,
            @Valid @ModelAttribute ComplaintCreateRequestDto request, Authentication actor) {
        return ApiResponseDto.success(complaints.submit(bookingId, request, actor.getName()), "Đã tiếp nhận khiếu nại.");
    }

    @PostMapping(path = "/submit-on-behalf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<DeliveryComplaintResponseDto> submitOnBehalf(@PathVariable Long bookingId,
            @Valid @ModelAttribute ComplaintOnBehalfRequestDto request, Authentication actor) {
        return ApiResponseDto.success(complaints.submitOnBehalf(bookingId, request, actor.getName()), "Đã ghi nhận khiếu nại thay khách.");
    }

    @PostMapping("/reply")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<DeliveryComplaintResponseDto> reply(@PathVariable Long bookingId,
            @Valid @RequestBody ComplaintReplyRequestDto request, Authentication actor) {
        return ApiResponseDto.success(complaints.reply(bookingId, request, actor.getName()), "Đã trả lời khiếu nại.");
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<DeliveryComplaintResponseDto> resolve(@PathVariable Long bookingId,
            @Valid @RequestBody ComplaintResolveRequestDto request, Authentication actor) {
        return ApiResponseDto.success(complaints.resolve(bookingId, request, actor.getName()), "Đã kết luận khiếu nại.");
    }
}
