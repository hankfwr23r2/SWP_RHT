package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.notification.NoticeResponse;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public List<NoticeResponse> findForUser(String role, String username) {
        return notificationRepository.findForUser(role, username).stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public void markAsRead(Integer id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
    }

    @Override
    @Transactional
    public void markAllAsRead(String role, String username) {
        notificationRepository.markAllAsRead(role, username);
    }

    @Override
    @Transactional
    public void delete(Integer id, String role, String username) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!matches(notification.getRecipientRole(), role)
                || !matches(notification.getRecipientUsername(), username)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        notificationRepository.delete(notification);
    }

    @Override
    @Transactional
    public void deleteAll(String role, String username) {
        notificationRepository.deleteForUser(role, username);
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

    private boolean matches(String recipient, String requested) {
        return recipient == null || recipient.equalsIgnoreCase(requested);
    }
}
