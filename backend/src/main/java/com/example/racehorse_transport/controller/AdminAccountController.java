package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.admin.AccountLogResponseDto;
import com.example.racehorse_transport.dto.admin.AccountResponseDto;
import com.example.racehorse_transport.dto.admin.CreateStaffRequestDto;
import com.example.racehorse_transport.dto.admin.UpdateAccountRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.AdminAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;
    private final JwtUtils jwtUtils;

    private String extractActor(String authHeader) {
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
        throw new org.springframework.security.access.AccessDeniedException("Không xác định được danh tính người thực hiện!");
    }

    // 1. API Lấy danh sách toàn bộ tài khoản
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<AccountResponseDto>>> getAllAccounts() {
        List<AccountResponseDto> accounts = adminAccountService.getAllAccounts();
        return ResponseEntity.ok(ApiResponseDto.success(accounts, "Lấy danh sách tài khoản thành công"));
    }

    // 2. API Admin tạo tài khoản nhân viên mới
    @PostMapping
    public ResponseEntity<ApiResponseDto<AccountResponseDto>> createStaffAccount(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody CreateStaffRequestDto request) {
        String actor = extractActor(authHeader);
        AccountResponseDto response = adminAccountService.createStaffAccount(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response, "Tạo tài khoản nhân viên thành công"));
    }

    // Cập nhật thông tin tài khoản (Họ tên, Email, Số điện thoại)
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponseDto<AccountResponseDto>> updateAccount(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable Integer userId,
            @Valid @RequestBody UpdateAccountRequestDto request) {
        String actor = extractActor(authHeader);
        AccountResponseDto response = adminAccountService.updateAccount(userId, request, actor);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Cập nhật thông tin tài khoản thành công"));
    }

    // 3. API Khóa / Mở khóa tài khoản
    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponseDto<Void>> toggleAccountStatus(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable Integer userId,
            @RequestBody Map<String, Object> body) {
        String actor = extractActor(authHeader);
        boolean lock = Boolean.TRUE.equals(body.get("locked"));
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;

        adminAccountService.toggleAccountStatus(userId, lock, actor, reason);
        String msg = lock ? "Đã khóa tài khoản thành công" : "Đã mở khóa tài khoản thành công";
        return ResponseEntity.ok(ApiResponseDto.success(null, msg));
    }

    // 4. API Đặt lại mật khẩu cho nhân viên
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<ApiResponseDto<Map<String, String>>> resetPassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable Integer userId) {
        String actor = extractActor(authHeader);
        String tempPassword = adminAccountService.resetPassword(userId, actor);
        return ResponseEntity.ok(ApiResponseDto.success(Map.of("tempPassword", tempPassword), "Đặt lại mật khẩu thành công"));
    }

    // 5. API Lấy lịch sử audit log của 1 tài khoản cụ thể
    @GetMapping("/{userId}/logs")
    public ResponseEntity<ApiResponseDto<List<AccountLogResponseDto>>> getAccountLogs(@PathVariable Integer userId) {
        List<AccountLogResponseDto> logs = adminAccountService.getAccountLogs(userId);
        return ResponseEntity.ok(ApiResponseDto.success(logs, "Lấy lịch sử thao tác tài khoản thành công"));
    }

    // 6. API Lấy toàn bộ audit log của tài khoản hệ thống
    @GetMapping("/logs")
    public ResponseEntity<ApiResponseDto<List<AccountLogResponseDto>>> getAllAccountLogs() {
        List<AccountLogResponseDto> logs = adminAccountService.getAllAccountLogs();
        return ResponseEntity.ok(ApiResponseDto.success(logs, "Lấy danh sách nhật ký tài khoản thành công"));
    }
}
