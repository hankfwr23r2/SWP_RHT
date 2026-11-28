package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.notification.NotificationResponseDto;
import com.example.racehorse_transport.entity.NotificationRecipient;
import com.example.racehorse_transport.entity.NotificationRecipientId;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.NotificationRecipientRepository;
import com.example.racehorse_transport.service.NotificationInboxService;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationInboxServiceImpl implements NotificationInboxService {

    // Chuông chỉ cần các tin gần đây
    static final int INBOX_SIZE = 50;

    private final NotificationRecipientRepository recipients;
    private final AppUserRepository users;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> mine(String username) {
        return recipients.findInbox(userId(username), PageRequest.of(0, INBOX_SIZE)).stream()
                .map(NotificationResponseDto::from).toList();
    }

    @Override
    @Transactional
    public void markRead(Long notificationId, String username) {
        NotificationRecipient recipient = find(notificationId, username);
        if (recipient.getReadAt() == null) recipient.setReadAt(clock.instant());
    }

    @Override
    @Transactional
    public void markAllRead(String username) {
        var now = clock.instant();
        recipients.findById_UserIdAndReadAtIsNull(userId(username)).forEach(r -> r.setReadAt(now));
    }

    @Override
    @Transactional
    public void delete(Long notificationId, String username) {
        recipients.delete(find(notificationId, username));
    }

    @Override
    @Transactional
    public void deleteAll(String username) {
        recipients.deleteAll(recipients.findById_UserId(userId(username)));
    }

    private NotificationRecipient find(Long notificationId, String username) {
        return recipients.findById(new NotificationRecipientId(notificationId, userId(username)))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo " + notificationId));
    }

    private Long userId(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản " + username)).getId();
    }
}
