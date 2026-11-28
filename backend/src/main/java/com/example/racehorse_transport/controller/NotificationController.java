package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.notification.NotificationResponseDto;
import com.example.racehorse_transport.service.NotificationInboxService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// Chuông thông báo: mọi tài khoản đã đăng nhập chỉ thấy và sửa thông báo gửi cho chính mình
@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationInboxService inbox;

    @GetMapping("/notifications/mine")
    public ApiResponseDto<List<NotificationResponseDto>> mine(Authentication authentication) {
        return ApiResponseDto.success(inbox.mine(authentication.getName()), "Thông báo của tôi");
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResponseDto<Void> markRead(@PathVariable Long id, Authentication authentication) {
        inbox.markRead(id, authentication.getName());
        return ApiResponseDto.success(null, "Đã đọc");
    }

    @PostMapping("/notifications/read-all")
    public ApiResponseDto<Void> markAllRead(Authentication authentication) {
        inbox.markAllRead(authentication.getName());
        return ApiResponseDto.success(null, "Đã đọc tất cả");
    }

    @DeleteMapping("/notifications/{id}")
    public ApiResponseDto<Void> delete(@PathVariable Long id, Authentication authentication) {
        inbox.delete(id, authentication.getName());
        return ApiResponseDto.success(null, "Đã xóa thông báo");
    }

    @DeleteMapping("/notifications/mine")
    public ApiResponseDto<Void> deleteAll(Authentication authentication) {
        inbox.deleteAll(authentication.getName());
        return ApiResponseDto.success(null, "Đã xóa tất cả thông báo");
    }
}
