package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.AuthResponseDto;
import com.example.racehorse_transport.dto.ChangePasswordRequestDto;
import com.example.racehorse_transport.dto.LoginRequestDto;
import com.example.racehorse_transport.dto.RegisterRequestDto;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.TermsAcceptance;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TermsAcceptanceRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.AdminAuditService;
import com.example.racehorse_transport.service.AuthService;
import com.example.racehorse_transport.service.CustomerProfileService;
import com.example.racehorse_transport.service.EmailVerificationService;
import com.example.racehorse_transport.util.TermsDocuments;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CustomerProfileService customerProfiles;
    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AdminAuditService audit;
    private final EmailVerificationService emailVerificationService;
    // Tiêm bằng trường (không qua constructor) để giữ nguyên chữ ký constructor mà unit test dựng tay đang dùng
    @Autowired
    private TermsAcceptanceRepository termsAcceptanceRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isInactiveStaff(String username) {
        return userRepository.findByUsername(username)
                .flatMap(user -> staffRepository.findById(user.getId()))
                .map(staff -> "INACTIVE".equalsIgnoreCase(staff.getEmploymentStatus()))
                .orElse(false);
    }

    @Override
    @Transactional
    public void register(RegisterRequestDto request) {
        // 1-2. Username hoặc email đã có tài khoản: trả 409 để form gắn lỗi vào ô Email (username đang chính là email)
        if (userRepository.existsByUsername(request.getUsername()) || userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email này đã có tài khoản. Hãy đăng nhập hoặc dùng email khác.");
        }

        if (!Boolean.TRUE.equals(request.getAcceptedTerms())) {
            throw new IllegalArgumentException("Bạn cần đồng ý Điều khoản vận chuyển để đăng ký.");
        }

        // 3. Tạo tài khoản User mới với mật khẩu mã hóa BCrypt
        User user = request.toEntity(passwordEncoder.encode(request.getPassword()));
        User savedUser = userRepository.save(user);
        termsAcceptanceRepository.save(TermsAcceptance.builder()
                .documentCode(TermsDocuments.TRANSPORT_TERMS)
                .documentVersion(TermsDocuments.TRANSPORT_TERMS_VERSION)
                .acceptedAt(Instant.now())
                .user(savedUser)
                .build());

        // 4. Mặc định tạo thông tin hồ sơ Customer tương ứng
        String fullName = request.getFullName() != null && !request.getFullName().isBlank()
                ? request.getFullName()
                : request.getUsername();
        customerProfiles.create(savedUser, fullName, request.getPhone(), request.getAddress());

        // 5. Gửi OTP xác thực email; chưa nhập OTP thì chưa đăng nhập được
        emailVerificationService.sendOtp(savedUser.getEmail());
    }

        // ==========================================
    // 1. ĐĂNG NHẬP DÀNH CHO KHÁCH HÀNG (CUSTOMER)
    // ==========================================
    @Override
    public AuthResponseDto loginCustomer(LoginRequestDto request) {
        // Tìm User theo username HOẶC email
        User user = userRepository.findByUsernameOrEmail(request.getUsername(), request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không đúng."));

        // So khớp mật khẩu đã hash
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không đúng.");
        }

        // KIỂM TRA BẢO MẬT: Bắt buộc phải có trong bảng Customer
        Customer customer = customerProfiles.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản này không phải là tài khoản khách hàng!"));

        // Email chưa xác thực (đăng ký mới hoặc tài khoản cũ): gửi OTP, chưa cấp token
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            emailVerificationService.sendOtp(user.getEmail());
            return AuthResponseDto.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role("CUSTOMER")
                    .emailVerificationRequired(true)
                    .build();
        }

        return customerAuth(user, customer);
    }

    @Override
    public void resendEmailOtp(String email) {
        userRepository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(user -> customerProfiles.existsById(user.getId()))
                .ifPresent(user -> emailVerificationService.sendOtp(user.getEmail()));
    }

    @Override
    public AuthResponseDto confirmEmail(String email, String otp) {
        User user = emailVerificationService.confirm(email, otp);
        Customer customer = customerProfiles.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản này không phải là tài khoản khách hàng!"));
        return customerAuth(user, customer);
    }

    private AuthResponseDto customerAuth(User user, Customer customer) {
        // Sinh JWT Token với Role là CUSTOMER
        String token = jwtUtils.generateToken(user.getUsername(), "CUSTOMER");

        return AuthResponseDto.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role("CUSTOMER")
                .fullName(customer.getFullName())
                .build();
    }

    // ==========================================
    // 2. ĐĂNG NHẬP DÀNH CHO NHÂN VIÊN NỘI BỘ (STAFF)
    // ==========================================
    @Override
    public AuthResponseDto loginStaff(LoginRequestDto request) {
        // Tìm User theo username HOẶC email
        User user = userRepository.findByUsernameOrEmail(request.getUsername(), request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản nội bộ hoặc mật khẩu không chính xác!"));

        // So khớp mật khẩu
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Tài khoản nội bộ hoặc mật khẩu không chính xác!");
        }

        // KIỂM TRA BẢO MẬT: Bắt buộc phải tồn tại trong bảng Staff
        Staff staff = staffRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản này không có quyền truy cập hệ thống nội bộ!"));

        // Kiểm tra trạng thái làm việc (nếu bị nghỉ việc/khóa thì chặn)
        if ("INACTIVE".equalsIgnoreCase(staff.getEmploymentStatus())) {
            throw new IllegalArgumentException("Tài khoản nhân viên đã bị vô hiệu hóa. Vui lòng liên hệ Admin!");
        }

        String staffRole = staff.getRole() != null ? staff.getRole().toUpperCase() : "STAFF";

        // Sinh JWT Token với đúng Role nội bộ (MANAGER, SPECIALIST, COORDINATOR...)
        String token = jwtUtils.generateToken(user.getUsername(), staffRole);

        return AuthResponseDto.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(staffRole)
                .fullName(user.getUsername())
                .staffCode(staff.getStaffCode())
                .build();
    }

    // ==========================================
    // 3. LÀM MỚI ACCESS TOKEN TỪ REFRESH TOKEN
    // ==========================================
    @Override
    public AuthResponseDto refreshToken(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Refresh token không hợp lệ hoặc đã hết hạn!");
        }

        String username = jwtUtils.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại!"));

        String role = "CUSTOMER";
        String fullName = null;
        String staffCode = null;

        Optional<Staff> staffOpt = staffRepository.findById(user.getId());
        if (staffOpt.isPresent()) {
            Staff staff = staffOpt.get();
            if ("INACTIVE".equalsIgnoreCase(staff.getEmploymentStatus())) {
                throw new IllegalArgumentException("Tài khoản nhân viên đã bị vô hiệu hóa!");
            }
            role = staff.getRole() != null ? staff.getRole().toUpperCase() : "STAFF";
            fullName = user.getUsername();
            staffCode = staff.getStaffCode();
        } else {
            Optional<Customer> customerOpt = customerProfiles.findById(user.getId());
            if (customerOpt.isPresent()) {
                fullName = customerOpt.get().getFullName();
            }
        }

        String newAccessToken = jwtUtils.generateToken(username, role);

        return AuthResponseDto.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(role)
                .fullName(fullName)
                .staffCode(staffCode)
                .build();
    }

    // ==========================================
    // 4. ĐỔI MẬT KHẨU CÁ NHÂN CHO NGƯỜI DÙNG ĐANG ĐĂNG NHẬP
    // ==========================================
    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequestDto request) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Thông tin xác thực không hợp lệ!");
        }

        // 1. Tìm user theo username hoặc email
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản người dùng!"));

        // 2. Kiểm tra mật khẩu hiện tại
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác!");
        }

        // 3. Kiểm tra xác nhận mật khẩu mới
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Xác nhận mật khẩu mới không trùng khớp!");
        }

        // 4. Kiểm tra mật khẩu mới không được trùng với mật khẩu hiện tại
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng với mật khẩu hiện tại!");
        }

        // 5. Cập nhật mật khẩu băm mới
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        audit.record("CHANGE_PASSWORD", "USER", user.getId(), user.getEmail(),
                "Người dùng tự đổi mật khẩu", null, null);
    }
}
