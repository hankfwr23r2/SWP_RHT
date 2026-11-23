package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.auth.AuthResponseDto;
import com.example.racehorse_transport.dto.auth.ChangePasswordRequestDto;
import com.example.racehorse_transport.dto.auth.LoginRequestDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRoleRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.AuthService;
import com.example.racehorse_transport.service.EmailVerificationService;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String CUSTOMER_ROLE = "CUSTOMER";
    // Thứ tự chọn vai trò chính khi một tài khoản có nhiều vai trò
    private static final List<String> ROLE_PRIORITY =
            List.of("ADMIN", "MANAGER", "COORDINATOR", "SPECIALIST", "DRIVER", "ESCORT", CUSTOMER_ROLE);
    private static final String BAD_CUSTOMER_LOGIN = "Email hoặc mật khẩu không đúng.";
    private static final String BAD_STAFF_LOGIN = "Tài khoản nội bộ hoặc mật khẩu không chính xác!";

    private final AppUserRepository users;
    private final UserRoleRepository userRoles;
    private final StaffRepository staff;
    private final PartyRepository parties;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailVerificationService emailVerification;

    @Override
    @Transactional
    public AuthResponseDto loginCustomer(LoginRequestDto request) {
        AppUser user = findByLogin(request.getUsername()).orElseThrow(() -> new IllegalArgumentException(BAD_CUSTOMER_LOGIN));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException(BAD_CUSTOMER_LOGIN);
        }
        List<String> roles = rolesOf(user);
        if (!roles.contains(CUSTOMER_ROLE)) {
            throw new IllegalArgumentException("Tài khoản này không phải là tài khoản khách hàng!");
        }
        requireActive(user);
        // Email chưa xác thực: gửi OTP, chưa cấp token
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            emailVerification.sendOtp(user.getEmail());
            return AuthResponseDto.verificationRequired(user, List.of(CUSTOMER_ROLE));
        }
        return customerAuth(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDto loginStaff(LoginRequestDto request) {
        AppUser user = findByLogin(request.getUsername()).orElseThrow(() -> new IllegalArgumentException(BAD_STAFF_LOGIN));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException(BAD_STAFF_LOGIN);
        }
        Staff profile = staff.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản này không có quyền truy cập hệ thống nội bộ!"));
        List<String> roles = rolesOf(user).stream().filter(role -> !CUSTOMER_ROLE.equals(role)).toList();
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Tài khoản này không có quyền truy cập hệ thống nội bộ!");
        }
        requireActive(user);
        if (profile.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw new IllegalArgumentException("Tài khoản nhân viên đã bị vô hiệu hóa. Vui lòng liên hệ Admin!");
        }
        return AuthResponseDto.from(user, jwtUtils.generateToken(user.getUsername(), roles), roles,
                profile.getFullName(), profile.getStaffCode());
    }

    @Override
    @Transactional
    public void resendEmailOtp(String email) {
        users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(user -> rolesOf(user).contains(CUSTOMER_ROLE))
                .ifPresent(user -> emailVerification.sendOtp(user.getEmail()));
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public AuthResponseDto confirmEmail(String email, String otp) {
        AppUser user = emailVerification.confirm(email, otp);
        if (!rolesOf(user).contains(CUSTOMER_ROLE)) {
            throw new IllegalArgumentException("Tài khoản này không phải là tài khoản khách hàng!");
        }
        requireActive(user);
        return customerAuth(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDto refreshToken(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Refresh token không hợp lệ hoặc đã hết hạn!");
        }
        AppUser user = users.findByUsername(jwtUtils.getUsernameFromToken(refreshToken))
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại!"));
        if (!isActive(user)) {
            throw new IllegalArgumentException("Tài khoản đã bị khóa hoặc vô hiệu hóa!");
        }
        Optional<Staff> profile = staff.findById(user.getId());
        if (profile.isPresent()) {
            List<String> roles = rolesOf(user).stream().filter(role -> !CUSTOMER_ROLE.equals(role)).toList();
            return AuthResponseDto.from(user, jwtUtils.generateToken(user.getUsername(), roles), roles,
                    profile.get().getFullName(), profile.get().getStaffCode());
        }
        return customerAuth(user);
    }

    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequestDto request) {
        AppUser user = findByLogin(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản người dùng!"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác!");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Xác nhận mật khẩu mới không trùng khớp!");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng với mật khẩu hiện tại!");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        users.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(String username) {
        return users.findByUsername(username).map(this::isActive).orElse(false);
    }

    private boolean isActive(AppUser user) {
        if (user.getStatus() != AppUserStatus.ACTIVE) {
            return false;
        }
        return staff.findById(user.getId())
                .map(profile -> profile.getEmploymentStatus() != EmploymentStatus.TERMINATED)
                .orElse(true);
    }

    private void requireActive(AppUser user) {
        if (user.getStatus() != AppUserStatus.ACTIVE) {
            throw new IllegalArgumentException("Tài khoản đã bị khóa hoặc vô hiệu hóa. Vui lòng liên hệ hỗ trợ!");
        }
    }

    private AuthResponseDto customerAuth(AppUser user) {
        List<String> roles = List.of(CUSTOMER_ROLE);
        String fullName = parties.findByUser_Id(user.getId()).map(Party::getDisplayName).orElse(user.getUsername());
        return AuthResponseDto.from(user, jwtUtils.generateToken(user.getUsername(), roles), roles, fullName, null);
    }

    // Vai trò của tài khoản, xếp theo ROLE_PRIORITY: phần tử đầu là vai trò chính
    private List<String> rolesOf(AppUser user) {
        return userRoles.findById_UserId(user.getId()).stream()
                .map(userRole -> userRole.getId().getRoleCode())
                .sorted(Comparator.comparingInt(role -> ROLE_PRIORITY.indexOf(role) < 0 ? ROLE_PRIORITY.size() : ROLE_PRIORITY.indexOf(role)))
                .toList();
    }

    // Đăng nhập bằng username hoặc email
    private Optional<AppUser> findByLogin(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        String value = login.trim();
        return users.findByUsername(value).or(() -> users.findByEmailIgnoreCase(value));
    }
}
