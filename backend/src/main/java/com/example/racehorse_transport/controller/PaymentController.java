package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.payment.*;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
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

    /**
     * 1. API Hủy đơn kèm chính sách hoàn cọc / phí phạt theo ngày (PRD mục 8.2 & 8.3)
     * POST /api/v1/payments/cancel
     */
    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<CancelWithRefundResponse>> cancelBookingWithRefund(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestParam("bookingId") String bookingId,
            @RequestBody(required = false) CancelBookingRequest request) {

        String username = extractUsername(authHeader);
        CancelWithRefundResponse response = paymentService.cancelWithRefundPolicy(bookingId, request, username);
        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }

    /**
     * 2. API Xem tóm tắt tình hình tài chính của đơn hàng
     * GET /api/v1/payments/booking/{bookingId}/summary
     */
    @GetMapping("/booking/{bookingId}/summary")
    public ResponseEntity<ApiResponse<PaymentSummaryResponse>> getPaymentSummary(
            @PathVariable("bookingId") String bookingId) {

        PaymentSummaryResponse response = paymentService.getPaymentSummary(bookingId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin tài chính đơn hàng thành công"));
    }

    /**
     * 3. API Xem lịch sử tất cả các đợt thanh toán của một đơn hàng
     * GET /api/v1/payments/booking/{bookingId}
     */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByBooking(
            @PathVariable("bookingId") String bookingId) {

        List<PaymentResponse> responses = paymentService.getPaymentsByBooking(bookingId);
        return ResponseEntity.ok(ApiResponse.success(responses, "Lấy lịch sử thanh toán thành công"));
    }

    /**
     * 4. API Xem chi tiết 1 giao dịch thanh toán
     * GET /api/v1/payments/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentDetail(
            @PathVariable("id") Integer id) {

        PaymentResponse response = paymentService.getPaymentDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết giao dịch thành công"));
    }
}
