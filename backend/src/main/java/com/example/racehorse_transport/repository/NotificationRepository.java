package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
