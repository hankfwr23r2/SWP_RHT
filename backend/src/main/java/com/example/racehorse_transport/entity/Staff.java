package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.EmploymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

// Hồ sơ nhân viên (1–1 với app_user).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "staff")
public class Staff {

    @Id
    @Column(name = "user_id")
    private Long userId;

    // Khóa chính kế thừa khóa của tài khoản
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "staff_code", length = 30, nullable = false)
    private String staffCode;

    @Column(name = "full_name", length = 200, nullable = false)
    private String fullName;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", length = 30, nullable = false)
    private EmploymentStatus employmentStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_hub_location_id", nullable = false)
    private Location homeHubLocation;

    @Column(name = "driver_license_class", length = 10)
    private String driverLicenseClass;

    @Column(name = "driver_license_expiry_date")
    private LocalDate driverLicenseExpiryDate;
}
