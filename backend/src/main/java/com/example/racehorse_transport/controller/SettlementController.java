package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.settlement.*;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final JwtUtils jwtUtils;

    private String extractUsername(String authHeader) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                return jwtUtils.getUsernameFromToken(token);
            }
        }
        return null;
    }

    /**
     * 1. API Kê khai chi phí phát sinh thực tế kèm ảnh hóa đơn (Evidence-First - Flow 6, bước 2)
     * POST /api/v1/settlements/{id}/submit-expenses
     */
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'ADMIN')")
    @PostMapping("/{id}/submit-expenses")
    public ResponseEntity<ApiResponse<BookingResponse>> submitExpenses(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestBody(required = false) SubmitExpensesRequest request) {

        String username = extractUsername(authHeader);
        BookingResponse response = settlementService.submitExpenses(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Kê khai chi phí phát sinh thành công"));
    }

    /**
     * 2. API Quản lý (Manager) đối soát chứng từ và phát hành Bảng quyết toán phát sinh (Flow 6, bước 3)
     * POST /api/v1/settlements/{id}/issue
     */
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @PostMapping("/{id}/issue")
    public ResponseEntity<ApiResponse<SettlementResponse>> issueSettlement(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody IssueSettlementRequest request) {

        String username = extractUsername(authHeader);
        SettlementResponse response = settlementService.issueSettlement(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Phát hành Bảng quyết toán phát sinh thành công (hiệu lực 24 giờ)"));
    }

    /**
     * 3. API Khách hàng thanh toán phát sinh (nếu có) + đánh giá 1-5 sao -> Hoàn tất đóng đơn (Flow 6, bước 4 & 5)
     * POST /api/v1/settlements/{id}/pay-and-complete
     */
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @PostMapping("/{id}/pay-and-complete")
    public ResponseEntity<ApiResponse<BookingResponse>> payAndComplete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody PaySettlementRequest request) {

        String username = extractUsername(authHeader);
        if (username == null) {
            throw new AccessDeniedException("Yêu cầu xác thực tài khoản khách hàng để thanh toán quyết toán");
        }
        BookingResponse response = settlementService.payAndComplete(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã thanh toán quyết toán và đánh giá thành công! Đơn hàng đã đóng hoàn tất."));
    }

    /**
     * 4. API Xem chi tiết Bảng quyết toán của một đơn hàng
     * GET /api/v1/settlements/{id}
     */
    @PreAuthorize("hasAnyRole('CUSTOMER', 'COORDINATOR', 'MANAGER', 'DRIVER', 'ESCORT', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlementDetail(
            @PathVariable("id") String id) {

        SettlementResponse response = settlementService.getSettlementDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin Bảng quyết toán thành công"));
    }
}
