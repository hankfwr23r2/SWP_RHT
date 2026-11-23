package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.TermsKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Generated;

import java.time.Instant;

// Bằng chứng khách đồng ý điều khoản.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "terms_acceptance")
public class TermsAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_kind", length = 30, nullable = false)
    private TermsKind termsKind;

    // Ví dụ 2027.1. Nội dung nằm ở file terms/<kind>/<version>.md
    @Column(name = "terms_version", length = 20, nullable = false)
    private String termsVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // NULL nếu đồng ý lúc đăng ký tài khoản
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "accepted_at", nullable = false, insertable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
