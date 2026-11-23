package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.NotificationRecipient;
import com.example.racehorse_transport.entity.NotificationRecipientId;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, NotificationRecipientId> {

    List<NotificationRecipient> findById_UserIdAndReadAtIsNull(Long userId);

    List<NotificationRecipient> findById_UserId(Long userId);

    // Hộp thư của một người: mới nhất trước
    @Query("select r from NotificationRecipient r join fetch r.notification n where r.id.userId = :userId order by n.createdAt desc, n.id desc")
    List<NotificationRecipient> findInbox(Long userId, Pageable page);
}
