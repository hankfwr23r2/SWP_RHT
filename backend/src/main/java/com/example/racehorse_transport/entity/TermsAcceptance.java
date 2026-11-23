package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Bằng chứng khách đồng ý điều khoản: văn bản nào, phiên bản nào, lúc nào (giờ server), của ai, cho đơn/ngựa nào
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "terms_acceptance")
public class TermsAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "document_code", nullable = false, length = 50)
    private String documentCode;

    @Column(name = "document_version", nullable = false, length = 50)
    private String documentVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id")
    private Horse horse;
}
