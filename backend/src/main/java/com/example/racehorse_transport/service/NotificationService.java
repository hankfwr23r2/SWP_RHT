package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.notification.NoticeResponse;

import java.util.List;

public interface NotificationService {
    List<NoticeResponse> findForUser(String role, String username);

    void markAsRead(Integer id);

    void markAllAsRead(String role, String username);

    void delete(Integer id, String role, String username);

    void deleteAll(String role, String username);
}
