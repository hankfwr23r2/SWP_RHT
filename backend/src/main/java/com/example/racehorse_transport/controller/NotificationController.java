package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.notification.NoticeResponse;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NoticeResponse>>> getNotifications(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "name", required = false) String name,
            Authentication authentication) {

        String queryRole = role;
        String queryUsername = name;

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            if (queryUsername == null || queryUsername.isBlank()) {
                queryUsername = authentication.getName();
            }
        }

        seedDefaultNoticesIfEmpty();

        List<Notification> notices = notificationRepository.findForUser(queryRole, queryUsername);
        List<NoticeResponse> responses = notices.stream().map(this::mapToResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, "Lấy danh sách thông báo thành công"));
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Integer id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
        return ResponseEntity.ok(ApiResponse.success(null, "Đã đánh dấu thông báo là đã đọc"));
    }

    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "name", required = false) String name) {
        notificationRepository.markAllAsRead(role, name);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã đánh dấu tất cả thông báo là đã đọc"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @PathVariable Integer id,
            @RequestParam String role,
            Authentication authentication) {
        String username = requireRecipient(role, authentication);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!matches(notification.getRecipientRole(), role)
                || !matches(notification.getRecipientUsername(), username)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        notificationRepository.delete(notification);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa thông báo"));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteAllNotifications(
            @RequestParam String role,
            Authentication authentication) {
        String username = requireRecipient(role, authentication);
        notificationRepository.deleteForUser(role, username);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa tất cả thông báo"));
    }

    private NoticeResponse mapToResponse(Notification n) {
        return NoticeResponse.builder()
                .id(String.valueOf(n.getId()))
                .role(n.getRecipientRole())
                .name(n.getRecipientUsername())
                .title(n.getTitle())
                .text(n.getMessage())
                .link(n.getLink())
                .bookingId(n.getBookingId())
                .at(n.getCreatedAt() != null ? n.getCreatedAt().toEpochMilli() : System.currentTimeMillis())
                .read(Boolean.TRUE.equals(n.getIsRead()))
                .build();
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

    private boolean matches(String recipient, String requested) {
        return recipient == null || recipient.equalsIgnoreCase(requested);
    }

    private synchronized void seedDefaultNoticesIfEmpty() {
        if (notificationRepository.count() == 0) {
            Instant now = Instant.now();
            List<Notification> seeds = new ArrayList<>();

            seeds.add(Notification.builder()
                    .recipientRole("manager")
                    .title("Đơn hàng mới cần tiếp nhận")
                    .message("Có đơn hàng mới gửi yêu cầu vận chuyển từ Đồng Nai đi Long An.")
                    .link("/manager/intake")
                    .bookingId("BK-1")
                    .isRead(false)
                    .createdAt(now.minusSeconds(7200))
                    .build());

            seeds.add(Notification.builder()
                    .recipientRole("customer")
                    .title("Báo giá vận chuyển đã được phát hành")
                    .message("Nhà xe đã gửi báo giá chính thức cho đơn của bạn. Vui lòng kiểm tra và đặt cọc trong 24h.")
                    .link("/orders")
                    .bookingId("BK-1")
                    .isRead(false)
                    .createdAt(now.minusSeconds(3600))
                    .build());

            seeds.add(Notification.builder()
                    .recipientRole("coordinator")
                    .title("Hồ sơ kiểm dịch đã duyệt")
                    .message("Kiểm dịch viên đã duyệt đạt hồ sơ thú y. Vui lòng xếp xe và lập lộ trình vận chuyển.")
                    .link("/coordinator/fleet-plan")
                    .bookingId("BK-1")
                    .isRead(false)
                    .createdAt(now.minusSeconds(1800))
                    .build());

            seeds.add(Notification.builder()
                    .recipientRole("driver")
                    .title("Lệnh điều xe mới")
                    .message("Bạn được phân công làm tài xế chính cho chuyến vận chuyển BK-1.")
                    .link("/driver")
                    .bookingId("BK-1")
                    .isRead(false)
                    .createdAt(now.minusSeconds(900))
                    .build());

            notificationRepository.saveAll(seeds);
            log.info("Initialized default system notifications.");
        }
    }
}
