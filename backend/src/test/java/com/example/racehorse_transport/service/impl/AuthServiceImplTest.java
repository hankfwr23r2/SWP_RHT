package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.AuthResponse;
import com.example.racehorse_transport.dto.LoginRequest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.SystemLogRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final SystemLogRepository systemLogRepository = mock(SystemLogRepository.class);

    private AuthServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                userRepository,
                customerRepository,
                staffRepository,
                passwordEncoder,
                jwtUtils,
                systemLogRepository);

        user = new User();
        user.setId(42);
        user.setUsername("Trần Dân");
        user.setEmail("staff@example.com");
        user.setPasswordHash("encoded-password");

        Staff staff = new Staff();
        staff.setId(42);
        staff.setRole("COORDINATOR");
        staff.setStaffCode("CO-02");
        staff.setEmploymentStatus("ACTIVE");

        when(userRepository.findByUsernameOrEmail("staff@example.com", "staff@example.com"))
                .thenReturn(Optional.of(user));
        when(staffRepository.findById(42)).thenReturn(Optional.of(staff));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(jwtUtils.generateToken("Trần Dân", "COORDINATOR")).thenReturn("access-token");
    }

    @Test
    void loginStaff_returnsTheAccountNameAsFullName() {
        AuthResponse response = service.loginStaff(
                LoginRequest.builder().username("staff@example.com").password("password").build());

        assertThat(response.getFullName()).isEqualTo("Trần Dân");
        assertThat(response.getStaffCode()).isEqualTo("CO-02");
    }
}
