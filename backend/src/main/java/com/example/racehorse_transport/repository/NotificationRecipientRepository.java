package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.NotificationRecipient;
import com.example.racehorse_transport.entity.NotificationRecipientId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, NotificationRecipientId> {

    List<NotificationRecipient> findById_UserIdAndReadAtIsNull(Long userId);
}
