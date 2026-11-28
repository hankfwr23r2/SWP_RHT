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

    @Query("""
            SELECT count(r) > 0 FROM NotificationRecipient r JOIN r.notification n
            WHERE r.id.userId = :userId AND n.title = :title AND n.createdAt >= :since
            """)
    boolean hasNoticeSince(Long userId, String title, java.time.Instant since);

    // Hộp thư của một người: mới nhất trước
    @Query("select r from NotificationRecipient r join fetch r.notification n where r.id.userId = :userId order by n.createdAt desc, n.id desc")
    List<NotificationRecipient> findInbox(Long userId, Pageable page);
}
