package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.notification.NotificationResponseDto;
import java.util.List;

// Hộp thư thông báo của người đang đăng nhập (chuông trên thanh menu). Ghi thông báo ở NotificationService.
public interface NotificationInboxService {

    List<NotificationResponseDto> mine(String username);

    void markRead(Long notificationId, String username);

    void markAllRead(String username);

    // Chỉ gỡ khỏi hộp thư của mình, người nhận khác vẫn thấy
    void delete(Long notificationId, String username);

    void deleteAll(String username);
}
