package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.admin.AccountLogResponse;
import com.example.racehorse_transport.dto.admin.AccountResponse;
import com.example.racehorse_transport.dto.admin.CreateStaffRequest;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.AdminAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;
    private final JwtUtils jwtUtils;

    private String extractActor(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                return jwtUtils.getUsernameFromToken(token);
            }
        }
        return null;
    }

    // 1. API Lấy danh sách toàn bộ tài khoản
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAllAccounts() {
        List<AccountResponse> accounts = adminAccountService.getAllAccounts();
        return ResponseEntity.ok(ApiResponse.success(accounts, "Lấy danh sách tài khoản thành công"));
    }

    // 2. API Admin tạo tài khoản nhân viên mới
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createStaffAccount(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody CreateStaffRequest request) {
        String actor = extractActor(authHeader);
        AccountResponse response = adminAccountService.createStaffAccount(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Tạo tài khoản nhân viên thành công"));
    }

    // 3. API Khóa / Mở khóa tài khoản
    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<Void>> toggleAccountStatus(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable Integer userId,
            @RequestBody Map<String, Object> body) {
        String actor = extractActor(authHeader);
        boolean lock = Boolean.TRUE.equals(body.get("locked"));
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;

        adminAccountService.toggleAccountStatus(userId, lock, actor, reason);
        String msg = lock ? "Đã khóa tài khoản thành công" : "Đã mở khóa tài khoản thành công";
        return ResponseEntity.ok(ApiResponse.success(null, msg));
    }

    // 4. API Đặt lại mật khẩu cho nhân viên
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable Integer userId) {
        String actor = extractActor(authHeader);
        String tempPassword = adminAccountService.resetPassword(userId, actor);
        return ResponseEntity.ok(ApiResponse.success(Map.of("tempPassword", tempPassword), "Đặt lại mật khẩu thành công"));
    }

    // 5. API Lấy lịch sử audit log của 1 tài khoản cụ thể
    @GetMapping("/{userId}/logs")
    public ResponseEntity<ApiResponse<List<AccountLogResponse>>> getAccountLogs(@PathVariable Integer userId) {
        List<AccountLogResponse> logs = adminAccountService.getAccountLogs(userId);
        return ResponseEntity.ok(ApiResponse.success(logs, "Lấy lịch sử thao tác tài khoản thành công"));
    }

    // 6. API Lấy toàn bộ audit log của tài khoản hệ thống
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<AccountLogResponse>>> getAllAccountLogs() {
        List<AccountLogResponse> logs = adminAccountService.getAllAccountLogs();
        return ResponseEntity.ok(ApiResponse.success(logs, "Lấy danh sách nhật ký tài khoản thành công"));
    }
}
