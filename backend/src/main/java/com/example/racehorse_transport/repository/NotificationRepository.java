package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Notification;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query(value = """
            WITH saved AS (
                INSERT INTO notification (booking_id, title, message, link_url, created_at)
                VALUES (:bookingId, :title, :message, :link, :createdAt) RETURNING id
            )
            SELECT id FROM saved
            """, nativeQuery = true)
    Long insertAt(Long bookingId, String title, String message, String link, Instant createdAt);
}
