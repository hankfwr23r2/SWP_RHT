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
     * 2. API Manager duyệt chứng từ nội bộ và hoàn tất đơn
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
        return ResponseEntity.ok(ApiResponse.success(response, "Đã duyệt chi phí nội bộ và hoàn tất đơn"));
    }

    /**
     * 4. API Xem chi tiết đối soát chi phí nội bộ
     * GET /api/v1/settlements/{id}
     */
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'DRIVER', 'ESCORT', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlementDetail(
            @PathVariable("id") String id) {

        SettlementResponse response = settlementService.getSettlementDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin đối soát nội bộ thành công"));
    }
}
