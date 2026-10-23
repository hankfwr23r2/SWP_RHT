package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.field.*;
import com.example.racehorse_transport.response.ApiResponse;
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

    // 1. Kíp xe check-in đến điểm giao đích & kích hoạt đếm thời gian 2h
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'ADMIN')")
    @PostMapping("/{id}/arrive")
    public ResponseEntity<ApiResponse<BookingResponse>> arriveDelivery(
            @PathVariable("id") Integer id,
            @RequestBody(required = false) DeliveryArriveRequest request) {
        BookingResponse response = deliveryService.arriveDelivery(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã check-in điểm giao đích, bắt đầu đếm thời gian bàn giao (2 giờ)"));
    }

    // 2. Ký Biên bản Bàn giao & Nghiệm thu (Không rating tài xế)
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'ADMIN')")
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<BookingResponse>> completeHandover(
            @PathVariable("id") Integer id,
            @Valid @RequestBody HandoverCompleteRequest request) {
        BookingResponse response = deliveryService.completeHandover(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã hoàn tất ký nhận bàn giao ngựa thành công"));
    }

    // 3. Xử lý ngoại lệ: Quá 2 giờ người nhận không tới / mất liên lạc / cơ sở chưa nhận
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'ADMIN')")
    @PostMapping("/{id}/timeout")
    public ResponseEntity<ApiResponse<BookingResponse>> reportDeliveryTimeout(
            @PathVariable("id") Integer id,
            @RequestBody DeliveryTimeoutRequest request) {
        BookingResponse response = deliveryService.reportDeliveryTimeout(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã ghi nhận ngoại lệ quá giờ bàn giao và kích hoạt an sinh ngựa"));
    }

    // 4. Kíp xe xác nhận xe đã về đến Trụ sở chính Việt Nam -> Xe về AVAILABLE
    @PreAuthorize("hasAnyRole('DRIVER', 'COORDINATOR', 'ADMIN')")
    @PostMapping("/vehicles/return-to-hq")
    public ResponseEntity<ApiResponse<String>> returnToHq(@RequestBody ReturnToHqRequest request) {
        deliveryService.returnToHq(request);
        return ResponseEntity.ok(ApiResponse.success("Xe đã về đến Trụ sở chính Việt Nam và được đưa về trạng thái AVAILABLE", "Thành công"));
    }
}
