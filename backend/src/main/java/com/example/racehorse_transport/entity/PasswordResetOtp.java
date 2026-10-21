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
@Table(name = "password_reset_otps",
        indexes = @Index(name = "idx_password_reset_otp_expiry", columnList = "ExpiresAt"),
        uniqueConstraints = @UniqueConstraint(name = "uk_password_reset_otp_email", columnNames = "Email"))
public class PasswordResetOtp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id", nullable = false)
    private Long id;

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
