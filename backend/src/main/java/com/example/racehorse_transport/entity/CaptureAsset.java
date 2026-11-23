package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "capture_asset", indexes = @Index(name = "idx_capture_booking", columnList = "booking_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CaptureAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 1000) private String url;
    @Column(name = "booking_id", nullable = false) private Integer bookingId;
    @Column(nullable = false, length = 100) private String tripId;
    @Column(nullable = false, length = 50) private String purpose;
    @Column(nullable = false, length = 100) private String target;
    @Column(nullable = false, length = 100) private String capturedBy;
    @Column(nullable = false) private Instant capturedAt;
    @Column(nullable = false) private Instant recordedAt;
}
