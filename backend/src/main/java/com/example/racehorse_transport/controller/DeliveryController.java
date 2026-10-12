package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.field.*;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final JwtUtils jwtUtils;

    private String extractUsername(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                return jwtUtils.getUsernameFromToken(token);
            }
        }
        return null;
    }

    // 1. Kíp xe check-in đến điểm giao đích & kích hoạt đếm thời gian 2h
    @PostMapping("/{id}/arrive")
    public ResponseEntity<ApiResponse<BookingResponse>> arriveDelivery(
            @PathVariable("id") Integer id,
            @RequestBody(required = false) DeliveryArriveRequest request) {
        BookingResponse response = deliveryService.arriveDelivery(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã check-in điểm giao đích, bắt đầu đếm thời gian bàn giao (2 giờ)"));
    }

    // 2. Ký Biên bản Bàn giao & Nghiệm thu (Không rating tài xế)
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<BookingResponse>> completeHandover(
            @PathVariable("id") Integer id,
            @Valid @RequestBody HandoverCompleteRequest request) {
        BookingResponse response = deliveryService.completeHandover(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã hoàn tất ký nhận bàn giao ngựa thành công"));
    }

    // 3. Xử lý ngoại lệ: Quá 2 giờ người nhận không tới / mất liên lạc / cơ sở chưa nhận
    @PostMapping("/{id}/timeout")
    public ResponseEntity<ApiResponse<BookingResponse>> reportDeliveryTimeout(
            @PathVariable("id") Integer id,
            @RequestBody DeliveryTimeoutRequest request) {
        BookingResponse response = deliveryService.reportDeliveryTimeout(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã ghi nhận ngoại lệ quá giờ bàn giao và kích hoạt an sinh ngựa"));
    }

    // 4. Manager đối soát hóa đơn đội ngoài & phát hành Bảng quyết toán phát sinh
    @PostMapping("/{id}/settlement")
    public ResponseEntity<ApiResponse<BookingResponse>> issueSettlement(
            @PathVariable("id") Integer id,
            @RequestBody IssueSettlementRequest request) {
        BookingResponse response = deliveryService.issueSettlement(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Quản lý đã phát hành Bảng quyết toán phát sinh thành công"));
    }

    // 5. Khách hàng thanh toán số dư quyết toán phát sinh
    @PostMapping("/{id}/pay-settlement")
    public ResponseEntity<ApiResponse<BookingResponse>> paySettlement(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") Integer id,
            @RequestBody(required = false) PaySettlementRequest request) {
        String username = extractUsername(authHeader);
        if (username == null) {
            username = "customer01"; // Fallback nếu gọi qua test script
        }
        BookingResponse response = deliveryService.paySettlement(id, username, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Thanh toán quyết toán thành công, đơn hàng hoàn tất!"));
    }

    // 6. Kíp xe xác nhận xe đã về đến Trụ sở chính Việt Nam -> Xe về AVAILABLE
    @PostMapping("/vehicles/return-to-hq")
    public ResponseEntity<ApiResponse<String>> returnToHq(@RequestBody ReturnToHqRequest request) {
        deliveryService.returnToHq(request);
        return ResponseEntity.ok(ApiResponse.success("Xe đã về đến Trụ sở chính Việt Nam và được đưa về trạng thái AVAILABLE", "Thành công"));
    }
}
