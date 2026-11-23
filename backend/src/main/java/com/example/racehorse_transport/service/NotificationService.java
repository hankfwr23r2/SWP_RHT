package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.notification.NoticeResponseDto;

import java.util.List;

public interface NotificationService {
    List<NoticeResponseDto> findForUser(String role, String username);

    void markAsRead(Integer id);

    void markAllAsRead(String role, String username);

    void delete(Integer id, String role, String username);

    void deleteAll(String role, String username);
}
