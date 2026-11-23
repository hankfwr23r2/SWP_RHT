package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Ai nhận một thông báo và đã đọc chưa.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_recipient")
public class NotificationRecipient {

    @EmbeddedId
    private NotificationRecipientId id;

    @MapsId("notificationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id")
    private Notification notification;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    // NULL = chưa đọc
    @Column(name = "read_at")
    private Instant readAt;
}
