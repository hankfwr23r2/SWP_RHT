package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "NOTIFICATION")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NotificationID", nullable = false)
    private Integer id;

    @Size(max = 100)
    @Column(name = "RecipientRole", length = 100)
    private String recipientRole;

    @Size(max = 255)
    @Column(name = "RecipientUsername")
    private String recipientUsername;

    @Size(max = 255)
    @Nationalized
    @Column(name = "Title")
    private String title;

    @Nationalized
    @Column(name = "Message", columnDefinition = "TEXT")
    private String message;

    @Size(max = 255)
    @Column(name = "Link")
    private String link;

    @Size(max = 50)
    @Column(name = "BookingID")
    private String bookingId;

    @Column(name = "IsRead", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "CreatedAt")
    private Instant createdAt;
}
