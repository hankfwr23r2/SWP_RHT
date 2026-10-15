package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
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

    @Value("${app.bootstrap-admin.username:}")
    private String bootstrapAdminUsername;

    @Value("${app.bootstrap-admin.email:}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap-admin.password:}")
    private String bootstrapAdminPassword;

    @Override
    public void run(String... args) {
        if (isBlank(bootstrapAdminUsername) || isBlank(bootstrapAdminEmail) || isBlank(bootstrapAdminPassword)) {
            log.info("Admin bootstrap skipped; configure APP_BOOTSTRAP_ADMIN_USERNAME, APP_BOOTSTRAP_ADMIN_EMAIL, and APP_BOOTSTRAP_ADMIN_PASSWORD to create the initial administrator.");
            return;
        }

        seedStaff(bootstrapAdminUsername.trim(), bootstrapAdminEmail.trim(), bootstrapAdminPassword, "ADMIN", "ADMIN-01");
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
