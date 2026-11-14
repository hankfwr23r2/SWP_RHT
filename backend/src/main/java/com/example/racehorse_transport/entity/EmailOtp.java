package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@org.hibernate.annotations.Check(constraints = "attempts >= 0 AND purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')")
@Table(name = "email_otp",
        indexes = @Index(name = "idx_email_otp_expiry", columnList = "ExpiresAt"),
        uniqueConstraints = @UniqueConstraint(name = "uk_email_otp_email_purpose", columnNames = {"Email", "purpose"}))
public class EmailOtp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id", nullable = false)
    private Long id;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "Email", nullable = false, length = 255)
    private String email;

    @Column(name = "OtpHash", nullable = false, length = 100)
    private String otpHash;

    @Column(name = "ExpiresAt", nullable = false)
    private Instant expiresAt;

    @Column(name = "LastSentAt", nullable = false)
    private Instant lastSentAt;

    @Column(name = "Attempts", nullable = false)
    private int attempts;
}
