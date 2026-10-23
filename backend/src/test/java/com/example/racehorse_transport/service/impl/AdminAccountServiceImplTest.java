package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.admin.AccountResponse;
import com.example.racehorse_transport.dto.admin.CreateStaffRequest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.SystemLogRepository;
import com.example.racehorse_transport.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAccountServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final SystemLogRepository systemLogRepository = mock(SystemLogRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private AdminAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAccountServiceImpl(
                userRepository,
                staffRepository,
                customerRepository,
                systemLogRepository,
                passwordEncoder);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42);
            return user;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(systemLogRepository.findByTargetTableAndTargetRecordIDOrderByCreatedAtDesc("USER", 42))
                .thenReturn(List.of());
    }

    @Test
    void toggleAccountStatus_refusesToLockTheSignedInAdmin() {
        User user = new User();
        user.setId(1);
        user.setUsername("admin01");
        Staff me = new Staff();
        me.setId(1);
        me.setUser(user);
        me.setRole("ADMIN");
        me.setEmploymentStatus("ACTIVE");
        when(staffRepository.findById(1)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.toggleAccountStatus(1, true, "admin01", "thử"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chính mình");
        verify(staffRepository, never()).save(any(Staff.class));
    }

    @Test
    void createStaffAccount_usesCoordinatorCodePrefix() {
        when(staffRepository.countByRoleIgnoreCase("coordinator")).thenReturn(1L);

        AccountResponse response = service.createStaffAccount(request("coordinator"));

        assertThat(response.getStaffCode()).isEqualTo("CO-02");
    }

    @Test
    void createStaffAccount_usesSpecialistCodePrefix() {
        when(staffRepository.countByRoleIgnoreCase("specialist")).thenReturn(1L);

        AccountResponse response = service.createStaffAccount(request("specialist"));

        assertThat(response.getStaffCode()).isEqualTo("SP-02");
    }

    @Test
    void createStaffAccount_usesAdminCodePrefix() {
        when(staffRepository.countByRoleIgnoreCase("admin")).thenReturn(1L);

        AccountResponse response = service.createStaffAccount(request("admin"));

        assertThat(response.getStaffCode()).isEqualTo("ADMIN-02");
    }

    @Test
    void createStaffAccount_usesDriverCodePrefix() {
        when(staffRepository.countByRoleIgnoreCase("driver")).thenReturn(1L);

        AccountResponse response = service.createStaffAccount(request("driver"));

        assertThat(response.getStaffCode()).isEqualTo("DR-02");
    }

    @Test
    void createStaffAccount_usesEscortCodePrefix() {
        when(staffRepository.countByRoleIgnoreCase("escort")).thenReturn(1L);

        AccountResponse response = service.createStaffAccount(request("escort"));

        assertThat(response.getStaffCode()).isEqualTo("ES-02");
    }

    private CreateStaffRequest request(String role) {
        return CreateStaffRequest.builder()
                .name("Test Staff")
                .email(role + "@example.com")
                .phone("0901234567")
                .role(role)
                .build();
    }
}
