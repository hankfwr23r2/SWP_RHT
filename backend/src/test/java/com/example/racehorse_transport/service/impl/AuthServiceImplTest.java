package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.AuthResponseDto;
import com.example.racehorse_transport.dto.LoginRequestDto;
import com.example.racehorse_transport.dto.RegisterRequestDto;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.AdminAuditService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);

    private AuthServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                userRepository,
                new CustomerProfileServiceImpl(userRepository, customerRepository, mock(HorseRepository.class), mock(BookingRepository.class)),
                staffRepository,
                passwordEncoder,
                jwtUtils,
                audit,
                null);

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
        AuthResponseDto response = service.loginStaff(
                LoginRequestDto.builder().username("staff@example.com").password("password").build());

        assertThat(response.getFullName()).isEqualTo("Trần Dân");
        assertThat(response.getStaffCode()).isEqualTo("CO-02");
    }

    @Test
    void register_rejectsEmailThatAlreadyHasAnAccountAsConflict() {
        when(userRepository.existsByEmail("da.co@gmail.com")).thenReturn(true);

        // IllegalStateException được GlobalExceptionHandler trả 409, form gắn lỗi vào ô Email
        assertThatThrownBy(() -> service.register(RegisterRequestDto.builder()
                .username("da.co@gmail.com").email("da.co@gmail.com").password("matkhau1")
                .fullName("Nguyễn Văn An").phone("0912345678").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Email này đã có tài khoản. Hãy đăng nhập hoặc dùng email khác.");
        verify(userRepository, never()).save(any());
    }
}
