package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.auth.AuthResponseDto;
import com.example.racehorse_transport.dto.auth.ChangePasswordRequestDto;
import com.example.racehorse_transport.dto.auth.LoginRequestDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.exception.InvalidTokenException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.exception.TooManyAttemptsException;
import com.example.racehorse_transport.policy.LoginAttemptPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.security.ClientApp;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.AuthService;
import com.example.racehorse_transport.service.EmailVerificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
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
    private static final String BAD_CUSTOMER_LOGIN = "Email hoặc mật khẩu không đúng.";
    private static final String BAD_STAFF_LOGIN = "Tài khoản nội bộ hoặc mật khẩu không chính xác!";
    private static final String NOT_INTERNAL_ACCOUNT = "Tài khoản này không có quyền truy cập hệ thống nội bộ!";

    private final AppUserRepository users;
    private final StaffRepository staff;
    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailVerificationService emailVerification;
    private final SystemConfigService configs;
    private final Clock clock;

    // Nhập sai mật khẩu vẫn ghi bộ đếm, nên không rollback khi ném IllegalArgumentException
    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public AuthResponseDto loginCustomer(LoginRequestDto request) {
        AppUser user = findByLogin(request.getUsername()).orElseThrow(() -> new IllegalArgumentException(BAD_CUSTOMER_LOGIN));
        checkPassword(user, request.getPassword(), BAD_CUSTOMER_LOGIN);
        if (!isCustomer(user)) {
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
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public AuthResponseDto loginStaff(LoginRequestDto request) {
        AppUser user = findByLogin(request.getUsername()).orElseThrow(() -> new IllegalArgumentException(BAD_STAFF_LOGIN));
        checkPassword(user, request.getPassword(), BAD_STAFF_LOGIN);
        if (isCustomer(user)) {
            throw new IllegalArgumentException(NOT_INTERNAL_ACCOUNT);
        }
        Staff profile = staffProfileOf(user);
        requireActive(user);
        if (profile.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
            throw new IllegalArgumentException("Tài khoản nhân viên đã bị vô hiệu hóa. Vui lòng liên hệ Admin!");
        }
        return staffAuth(user, profile);
    }

    @Override
    @Transactional
    public void resendEmailOtp(String email) {
        users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(this::isCustomer)
                .ifPresent(user -> emailVerification.sendOtp(user.getEmail()));
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public AuthResponseDto confirmEmail(String email, String otp) {
        AppUser user = emailVerification.confirm(email, otp);
        if (!isCustomer(user)) {
            throw new IllegalArgumentException("Tài khoản này không phải là tài khoản khách hàng!");
        }
        requireActive(user);
        return customerAuth(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDto refreshToken(String refreshToken, ClientApp app) {
        if (!jwtUtils.validateRefreshToken(refreshToken)) {
            throw new InvalidTokenException("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }
        AppUser user = users.findByUsername(jwtUtils.getUsernameFromToken(refreshToken))
                .orElseThrow(() -> new InvalidTokenException("Người dùng không tồn tại!"));
        if (!isActive(user)) {
            throw new InvalidTokenException("Tài khoản đã bị khóa hoặc vô hiệu hóa!");
        }
        // Đã đăng xuất hoặc đổi mật khẩu sau khi cấp token này
        if (jwtUtils.getVersionFromToken(refreshToken) != user.getTokenVersion()) {
            throw new InvalidTokenException("Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.");
        }
        // Cookie của trang khách chỉ cấp token cho khách, cookie của backoffice chỉ cấp cho nhân viên
        if (isCustomer(user) != (app == ClientApp.CUSTOMER)) {
            throw new InvalidTokenException("Phiên đăng nhập không thuộc ứng dụng này.");
        }
        return isCustomer(user) ? customerAuth(user) : staffAuth(user, staffProfileOf(user));
    }

    @Override
    @Transactional
    public AuthResponseDto changePassword(String username, ChangePasswordRequestDto request) {
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
        // Mọi phiên cũ (kể cả của người lấy được mật khẩu cũ) hết hiệu lực; máy đang dùng nhận token mới
        user.setTokenVersion(user.getTokenVersion() + 1);
        users.save(user);
        return isCustomer(user) ? customerAuth(user) : staffAuth(user, staffProfileOf(user));
    }

    // Đăng xuất: tăng phiên bản để mọi token của tài khoản hết hiệu lực trên mọi máy, kể cả sau khi máy chủ khởi động lại.
    // Người đăng xuất lấy từ refresh token hoặc access token còn hiệu lực; không có token nào hợp lệ thì không làm gì
    @Override
    @Transactional
    public void logout(String refreshToken, String accessToken) {
        String token = jwtUtils.validateRefreshToken(refreshToken) ? refreshToken
                : jwtUtils.validateAccessToken(accessToken) ? accessToken : null;
        if (token == null) {
            return;
        }
        users.findByUsername(jwtUtils.getUsernameFromToken(token))
                .filter(user -> jwtUtils.getVersionFromToken(token) == user.getTokenVersion())
                .ifPresent(user -> {
                    user.setTokenVersion(user.getTokenVersion() + 1);
                    users.save(user);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSessionValid(String username, int tokenVersion) {
        return users.findByUsername(username)
                .filter(user -> user.getTokenVersion() == tokenVersion)
                .map(this::isActive)
                .orElse(false);
    }

    // Khóa tạm khi nhập sai liên tiếp (LoginAttemptPolicy). Đang khóa thì từ chối cả mật khẩu đúng
    private void checkPassword(AppUser user, String password, String badLoginMessage) {
        Instant now = clock.instant();
        if (LoginAttemptPolicy.isLocked(user.getLockedUntil(), now)) {
            throw new TooManyAttemptsException("Tài khoản tạm khóa do nhập sai mật khẩu nhiều lần. Thử lại sau %d phút."
                    .formatted(LoginAttemptPolicy.minutesLeft(user.getLockedUntil(), now)));
        }
        boolean matches = passwordEncoder.matches(password, user.getPasswordHash());
        if (matches && user.getFailedLoginCount() == 0 && user.getLockedUntil() == null) {
            return;
        }
        LoginAttemptPolicy.Attempts attempts = matches ? LoginAttemptPolicy.afterSuccess()
                : LoginAttemptPolicy.afterFailure(user.getFailedLoginCount(), configs.getInt("LOGIN_MAX_FAILED_ATTEMPTS"),
                        configs.getInt("LOGIN_LOCK_MINUTES"), now);
        user.setFailedLoginCount(attempts.failedCount());
        user.setLockedUntil(attempts.lockedUntil());
        users.save(user);
        if (!matches) {
            throw new IllegalArgumentException(badLoginMessage);
        }
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
        List<String> roles = rolesOf(user);
        String fullName = customers.findById(user.getId()).map(Customer::getFullName).orElse(user.getUsername());
        return AuthResponseDto.from(user, jwtUtils.generateToken(user.getUsername(), roles, user.getTokenVersion()),
                jwtUtils.generateRefreshToken(user.getUsername(), user.getTokenVersion()), roles, fullName, null);
    }

    private AuthResponseDto staffAuth(AppUser user, Staff profile) {
        List<String> roles = rolesOf(user);
        return AuthResponseDto.from(user, jwtUtils.generateToken(user.getUsername(), roles, user.getTokenVersion()),
                jwtUtils.generateRefreshToken(user.getUsername(), user.getTokenVersion()), roles,
                profile.getFullName(), profile.getStaffCode());
    }

    private Staff staffProfileOf(AppUser user) {
        return staff.findById(user.getId()).orElseThrow(() -> new IllegalArgumentException(NOT_INTERNAL_ACCOUNT));
    }

    private boolean isCustomer(AppUser user) {
        return CUSTOMER_ROLE.equals(user.getRole().getCode());
    }

    // Mỗi tài khoản một vai trò. Trả danh sách để giữ claim "roles" cho giao diện
    private List<String> rolesOf(AppUser user) {
        return List.of(user.getRole().getCode());
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
