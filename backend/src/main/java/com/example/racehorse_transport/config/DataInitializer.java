package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        // 1. Tắt IDENTITY_CACHE trên SQL Server để ngăn ngừa ID bị nhảy số khi restart server
        try {
            jdbcTemplate.execute("ALTER DATABASE SCOPED CONFIGURATION SET IDENTITY_CACHE = OFF;");
            log.info("SQL Server IDENTITY_CACHE has been set to OFF.");
        } catch (Exception e) {
            log.warn("Could not set IDENTITY_CACHE: {}", e.getMessage());
        }

        // 2. Tự động khởi tạo tài khoản Admin mặc định
        seedStaff("admin01", "admin01@gmail.com", "admin123", "ADMIN", "ADMIN-01");

        // 3. Tự động khởi tạo nhân sự vận hành cốt lõi nếu chưa có
        seedStaff("manager01", "manager01@gmail.com", "Password@123", "MANAGER", "MG-01");
        seedStaff("coordinator01", "coordinator01@gmail.com", "Password@123", "COORDINATOR", "CO-01");
        seedStaff("specialist01", "specialist01@gmail.com", "Password@123", "SPECIALIST", "SP-01");

        // Drivers (Tài xế)
        seedStaff("driver01", "driver01@gmail.com", "Password@123", "DRIVER", "TX-01");
        seedStaff("driver02", "driver02@gmail.com", "Password@123", "DRIVER", "TX-02");
        seedStaff("driver03", "driver03@gmail.com", "Password@123", "DRIVER", "TX-03");

        // Escorts (Hộ tống / Chuyên viên thú y)
        seedStaff("escort01", "escort01@gmail.com", "Password@123", "ESCORT", "NV-01");
        seedStaff("escort02", "escort02@gmail.com", "Password@123", "ESCORT", "NV-02");
        seedStaff("escort03", "escort03@gmail.com", "Password@123", "ESCORT", "NV-03");
    }

    private void seedStaff(String username, String email, String rawPassword, String role, String staffCode) {
        if (userRepository.findByEmail(email).isEmpty() && userRepository.findByUsername(username).isEmpty()) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user = userRepository.save(user);

            Staff staff = new Staff();
            staff.setUser(user);
            staff.setRole(role);
            staff.setStaffCode(staffCode);
            staff.setEmploymentStatus("ACTIVE");
            staff.setHireDate(LocalDate.now());
            staffRepository.save(staff);

            log.info(">>> Seeded staff account: role={}, username={}, email={} (UserID: {})", role, username, email, user.getId());
        }
    }
}
