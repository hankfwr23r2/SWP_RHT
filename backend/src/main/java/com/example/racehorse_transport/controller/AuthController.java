package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.AuthResponse;
import com.example.racehorse_transport.dto.EmailVerificationRequest;
import com.example.racehorse_transport.dto.LoginRequest;
import com.example.racehorse_transport.dto.RegisterRequest;
import com.example.racehorse_transport.dto.PasswordResetOtpRequest;
import com.example.racehorse_transport.dto.ResetPasswordWithOtpRequest;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.AuthService;
import com.example.racehorse_transport.service.PasswordResetService;
import com.example.racehorse_transport.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final JwtUtils jwtUtils;

    @PostMapping("/password-reset/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestPasswordResetOtp(@Valid @RequestBody PasswordResetOtpRequest request) {
        passwordResetService.requestOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null,
                "Nếu email có tài khoản, mã OTP sẽ được gửi đến hộp thư của bạn."));
    }

    @PostMapping("/password-reset/complete")
    public ResponseEntity<ApiResponse<Void>> resetPasswordWithOtp(@Valid @RequestBody ResetPasswordWithOtpRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Đặt lại mật khẩu thành công. Bạn có thể đăng nhập."));
    }

    // API Đăng ký tài khoản
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công!", "Đăng ký thành công. Hãy nhập mã OTP đã gửi về email."));
    }

    // API Gửi lại OTP xác thực email
    @PostMapping("/email-verification/resend")
    public ResponseEntity<ApiResponse<Void>> resendEmailOtp(@Valid @RequestBody EmailVerificationRequest request) {
        authService.resendEmailOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null, "Nếu email có tài khoản, mã OTP mới sẽ được gửi đến hộp thư của bạn."));
    }

    // API Nhập OTP xác thực email, đúng thì đăng nhập luôn
    @PostMapping("/email-verification/confirm")
    public ResponseEntity<ApiResponse<AuthResponse>> confirmEmail(@Valid @RequestBody EmailVerificationRequest request) {
        return withRefreshCookie(authService.confirmEmail(request.getEmail(), request.getOtp()), "Xác thực email thành công");
    }

    // API Đăng nhập Khách hàng
    @PostMapping("/customer/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginCustomer(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.loginCustomer(request);
        if (authResponse.isEmailVerificationRequired()) {
            return ResponseEntity.ok(ApiResponse.success(authResponse, "Hãy nhập mã OTP đã gửi về email để xác thực tài khoản."));
        }
        return withRefreshCookie(authResponse, "Đăng nhập Khách hàng thành công");
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(AuthResponse authResponse, String message) {
        ResponseCookie refreshCookie = jwtUtils.generateRefreshCookie(authResponse.getUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(authResponse, message));
    }

    // API Đăng nhập Nhân viên nội bộ
    @PostMapping("/staff/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginStaff(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.loginStaff(request);
        ResponseCookie refreshCookie = jwtUtils.generateRefreshCookie(authResponse.getUsername());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(authResponse, "Đăng nhập Nội bộ thành công"));
    }

    // API Làm mới Access Token từ HttpOnly Cookie
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(HttpServletRequest request) {
        String refreshToken = jwtUtils.getRefreshTokenFromCookies(request);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Không tìm thấy Refresh Token trong cookie!"));
        }
        AuthResponse authResponse = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Làm mới Access Token thành công"));
    }

    // API Đăng xuất - Thu hồi token và Xóa HttpOnly Cookie
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        // 1. Thu hồi Refresh Token từ cookie
        String refreshToken = jwtUtils.getRefreshTokenFromCookies(request);
        if (refreshToken != null) {
            jwtUtils.blacklistToken(refreshToken);
        }

        // 2. Thu hồi Access Token từ Authorization header nếu có
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtUtils.blacklistToken(authHeader.substring(7));
        }

        ResponseCookie cleanCookie = jwtUtils.getCleanRefreshCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(ApiResponse.success("Đăng xuất thành công, token đã bị thu hồi", "Thành công"));
    }

    // API Đổi mật khẩu cá nhân cho người dùng đang đăng nhập
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody com.example.racehorse_transport.dto.ChangePasswordRequest request) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Vui lòng đăng nhập để đổi mật khẩu!"));
        }

        String token = authHeader.substring(7);
        if (!jwtUtils.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Phiên đăng nhập không hợp lệ hoặc đã hết hạn!"));
        }

        String username = jwtUtils.getUsernameFromToken(token);
        authService.changePassword(username, request);

        return ResponseEntity.ok(ApiResponse.success(null, "Đổi mật khẩu thành công!"));
    }
}
