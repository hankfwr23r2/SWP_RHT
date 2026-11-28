package com.example.racehorse_transport.dto.notification;

import com.example.racehorse_transport.entity.NotificationRecipient;
import java.time.Instant;

// Một thông báo trong hộp thư của người đang đăng nhập
public record NotificationResponseDto(Long id, String title, String message, String linkUrl, Instant createdAt, boolean read) {

    public static NotificationResponseDto from(NotificationRecipient recipient) {
        var notification = recipient.getNotification();
        return new NotificationResponseDto(notification.getId(), notification.getTitle(), notification.getMessage(),
                notification.getLinkUrl(), notification.getCreatedAt(), recipient.getReadAt() != null);
    }
}
