package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.notification.NoticeResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<NoticeResponseDto>>> getNotifications(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "name", required = false) String name,
            Authentication authentication) {

        // Chỉ đọc thông báo của vai trò và tài khoản đang đăng nhập (thông báo nội bộ có lý do trả lại, chi phí…)
        String queryUsername = requireRecipient(role, authentication);


        List<NoticeResponseDto> responses = notificationService.findForUser(role, queryUsername);
        return ResponseEntity.ok(ApiResponseDto.success(responses, "Lấy danh sách thông báo thành công"));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponseDto<Void>> markAsRead(@PathVariable Integer id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Đã đánh dấu thông báo là đã đọc"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponseDto<Void>> markAllAsRead(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "name", required = false) String name,
            Authentication authentication) {
        notificationService.markAllAsRead(role, requireRecipient(role, authentication));
        return ResponseEntity.ok(ApiResponseDto.success(null, "Đã đánh dấu tất cả thông báo là đã đọc"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDto<Void>> deleteNotification(
            @PathVariable Integer id,
            @RequestParam String role,
            Authentication authentication) {
        String username = requireRecipient(role, authentication);
        notificationService.delete(id, role, username);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Đã xóa thông báo"));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDto<Void>> deleteAllNotifications(
            @RequestParam String role,
            Authentication authentication) {
        String username = requireRecipient(role, authentication);
        notificationService.deleteAll(role, username);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Đã xóa tất cả thông báo"));
    }

    private String requireRecipient(String role, Authentication authentication) {
        if (role == null || role.isBlank() || authentication == null
                || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        String requiredAuthority = "ROLE_" + role.toUpperCase();
        boolean authorized = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(requiredAuthority::equals);
        if (!authorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return authentication.getName();
    }
}
