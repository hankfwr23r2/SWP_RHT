package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.entity.NotificationRecipient;
import com.example.racehorse_transport.entity.NotificationRecipientId;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.NotificationRecipientRepository;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.service.NotificationService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Ghi một thông báo và danh sách người nhận (chưa đọc). Phần đọc và đánh dấu đã đọc làm ở task B6.
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_LINK_LENGTH = 500;

    private final NotificationRepository notifications;
    private final NotificationRecipientRepository recipients;
    private final AppUserRepository users;
    private final BookingRepository bookings;
    private final com.example.racehorse_transport.repository.StaffRepository staff;

    @Override
    @Transactional
    public void notifyManagers(Long bookingId, String title, String message, String link) {
        var managers = staff.findUserIdsByRoleAndStatus("MANAGER", com.example.racehorse_transport.entity.enums.AppUserStatus.ACTIVE);
        if (!managers.isEmpty()) {
            notify(managers, bookingId, title, message, link);
        }
    }

    @Override
    @Transactional
    public void notify(List<Long> recipientUserIds, Long bookingId, String title, String message, String link) {
        notifyAt(recipientUserIds, bookingId, title, message, link, null);
    }

    @Override
    @Transactional
    public void notifyAt(List<Long> recipientUserIds, Long bookingId, String title, String message, String link, Instant createdAt) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            throw new IllegalArgumentException("Thông báo phải có ít nhất một người nhận.");
        }
        if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Tiêu đề thông báo bắt buộc và tối đa " + MAX_TITLE_LENGTH + " ký tự.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Nội dung thông báo bắt buộc.");
        }
        if (link != null && link.length() > MAX_LINK_LENGTH) {
            throw new IllegalArgumentException("Đường dẫn thông báo tối đa " + MAX_LINK_LENGTH + " ký tự.");
        }
        Set<Long> userIds = new LinkedHashSet<>(recipientUserIds);
        Map<Long, AppUser> found = users.findAllById(userIds).stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        for (Long userId : userIds) {
            if (!found.containsKey(userId)) {
                throw new ResourceNotFoundException("Không tìm thấy tài khoản " + userId);
            }
        }

        Booking booking = bookingId == null ? null : findBooking(bookingId);
        Notification saved = save(booking, title, message, link, createdAt);

        recipients.saveAll(userIds.stream().map(userId -> {
            NotificationRecipient recipient = new NotificationRecipient();
            recipient.setId(new NotificationRecipientId(saved.getId(), userId));
            recipient.setNotification(saved);
            recipient.setUser(found.get(userId));
            return recipient;
        }).toList());
    }

    private Notification save(Booking booking, String title, String message, String link, Instant createdAt) {
        if (createdAt != null) {
            Long id = notifications.insertAt(booking == null ? null : booking.getId(), title, message, link, createdAt);
            return notifications.getReferenceById(id);
        }
        Notification notification = new Notification();
        notification.setBooking(booking);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLinkUrl(link);
        return notifications.save(notification);
    }

    private Booking findBooking(Long bookingId) {
        return bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn " + bookingId));
    }
}
