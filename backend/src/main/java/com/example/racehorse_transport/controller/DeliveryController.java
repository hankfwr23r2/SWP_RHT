package com.example.racehorse_transport.controller;


import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.field.DeliveryArriveRequestDto;
import com.example.racehorse_transport.dto.field.DeliveryTimeoutRequestDto;
import com.example.racehorse_transport.dto.field.HandoverCompleteRequestDto;
import com.example.racehorse_transport.dto.field.ReturnToHqRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final BookingQueryService bookingQueries;

    // 1. Kíp xe check-in đến điểm giao đích & kích hoạt đếm thời gian 2h
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'ADMIN')")
    @PostMapping("/{id}/arrive")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> arriveDelivery(
            @PathVariable("id") String id,
            @RequestBody(required = false) DeliveryArriveRequestDto request) {
        BookingResponseDto response = deliveryService.arriveDelivery(bookingQueries.resolveBookingId(id), request);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã check-in điểm giao đích, bắt đầu đếm thời gian bàn giao (2 giờ)"));
    }

    // 2. Ký Biên bản Bàn giao & Nghiệm thu (Không rating tài xế)
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'ADMIN')")
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> completeHandover(
            @PathVariable("id") String id,
            @Valid @RequestBody HandoverCompleteRequestDto request) {
        BookingResponseDto response = deliveryService.completeHandover(bookingQueries.resolveBookingId(id), request);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã hoàn tất ký nhận bàn giao ngựa thành công"));
    }

    // 3. Xử lý ngoại lệ: Quá 2 giờ người nhận không tới / mất liên lạc / cơ sở chưa nhận
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'ADMIN')")
    @PostMapping("/{id}/timeout")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> reportDeliveryTimeout(
            @PathVariable("id") String id,
            @RequestBody DeliveryTimeoutRequestDto request,
            org.springframework.security.core.Authentication authentication) {
        String role = authentication.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", ""))
                .filter(r -> java.util.Set.of("DRIVER", "ESCORT", "ADMIN").contains(r)).findFirst().orElse("");
        BookingResponseDto response = deliveryService.reportDeliveryTimeout(bookingQueries.resolveBookingId(id), request, authentication.getName(), role);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã ghi nhận ngoại lệ quá giờ bàn giao và kích hoạt an sinh ngựa"));
    }

    // 4. Kíp xe xác nhận xe đã về đến Trụ sở chính Việt Nam -> Xe về AVAILABLE
    @PreAuthorize("hasAnyRole('DRIVER', 'COORDINATOR', 'ADMIN')")
    @PostMapping("/vehicles/return-to-hq")
    public ResponseEntity<ApiResponseDto<String>> returnToHq(@RequestBody ReturnToHqRequestDto request) {
        deliveryService.returnToHq(request);
        return ResponseEntity.ok(ApiResponseDto.success("Xe đã về đến Trụ sở chính Việt Nam và được đưa về trạng thái AVAILABLE", "Thành công"));
    }
}
