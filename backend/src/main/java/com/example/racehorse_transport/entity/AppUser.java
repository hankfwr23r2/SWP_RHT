package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.AppUserStatus;

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

// Tài khoản đăng nhập. Mỗi tài khoản có đúng một vai trò; hồ sơ nằm ở customer hoặc staff.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", length = 50, nullable = false)
    private String username;

    @Column(name = "email", length = 254, nullable = false)
    private String email;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "is_email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "preferred_language", length = 5, nullable = false)
    private String preferredLanguage = "vi";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_code", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private AppUserStatus status;

    // Phiên bản phiên đăng nhập: token mang số khác số này thì hết hiệu lực. AuthService và PasswordResetService tăng
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    // Số lần nhập sai mật khẩu liên tiếp và hạn khóa tạm, do AuthService ghi theo LoginAttemptPolicy
    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
